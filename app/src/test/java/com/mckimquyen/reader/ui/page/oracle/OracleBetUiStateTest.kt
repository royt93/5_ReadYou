package com.mckimquyen.reader.ui.page.oracle

import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.UserWallet
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OracleBetUiStateTest {

    private val expires = 1_000_000L
    private val card = OracleClaimCard("a", "Q?", expires, "c")
    private fun wallet(balance: Long) = UserWallet(balance = balance, lastGrantEpochDay = 1)
    private fun bet(status: OracleBetStatus, exp: Long = expires) =
        OracleBet(id = 1, articleId = "a", question = "Q?", expiresAt = exp, side = OracleBetSide.YES, stake = 100, status = status, placedAt = 1)

    @Test fun noWalletYet_showsTheStartingBalance_andCanBet() {
        val s = buildOracleBetUiState(card, null, null, now = expires - 1)
        assertEquals(1000L, s.balance)
        assertEquals(OracleBetPhase.CAN_BET, s.phase)
        assertNull(s.bet)
    }

    @Test fun lowBalance_isOutOfPoints_untilTheDeadline() {
        assertEquals(OracleBetPhase.OUT_OF_POINTS, buildOracleBetUiState(card, wallet(49), null, expires - 1).phase)
        assertEquals(OracleBetPhase.CAN_BET, buildOracleBetUiState(card, wallet(50), null, expires - 1).phase)
        assertEquals(OracleBetPhase.CLOSED_NO_BET, buildOracleBetUiState(card, wallet(0), null, expires).phase)
    }

    @Test fun openBet_isOpenUntilTheDeadline_thenAwaitsTheResult() {
        assertEquals(OracleBetPhase.BET_OPEN, buildOracleBetUiState(card, wallet(900), bet(OracleBetStatus.OPEN), expires - 1).phase)
        assertEquals(OracleBetPhase.AWAITING_RESULT, buildOracleBetUiState(card, wallet(900), bet(OracleBetStatus.OPEN), expires).phase)
    }

    @Test fun settledBet_isSettled() {
        OracleBetStatus.values().filter { it != OracleBetStatus.OPEN }.forEach {
            assertEquals("$it", OracleBetPhase.SETTLED, buildOracleBetUiState(card, wallet(900), bet(it), expires + 1).phase)
        }
    }

    @Test fun theDeadlineOfAnExistingBetComesFromTheBet_notFromTheCard() {
        // The prediction was re-dated after the bet: the bet keeps the date it was placed under.
        val s = buildOracleBetUiState(card.copy(expiresAt = expires + 1_000), wallet(900), bet(OracleBetStatus.OPEN, exp = expires), now = expires)
        assertEquals(OracleBetPhase.AWAITING_RESULT, s.phase)
    }

    @Test fun balanceComesFromTheWallet_andTheBetIsPassedThrough() {
        val b = bet(OracleBetStatus.WON)
        val s = buildOracleBetUiState(card, wallet(1234), b, expires + 1)
        assertEquals(1234L, s.balance)
        assertEquals(b, s.bet)
    }

    // --- source: the view model never reaches for ads, links or accounts

    private val source = File("src/main/java/com/mckimquyen/reader/ui/page/oracle/OracleBetting.kt").readLines()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }
        .joinToString("\n")

    @Test fun sourceMentionsNoAdsLinksOrAccounts() {
        // "accountId" is allowed since ORACLE-03: the evidence search is scoped to the current account (tested in OracleEvidenceViewModelTest).
        listOf("AdManager", "showRewarded", "Rewarded", "Admob", "link", "feedId").forEach {
            assertFalse("OracleBetting.kt must not mention '$it'", source.contains(it, ignoreCase = true))
        }
    }

    @Test fun sourceIsAHiltViewModelOnTheIoDispatcher_andLaunchesOnlyInTheViewModelScope() {
        assertTrue(source.contains("@HiltViewModel"))
        assertTrue(source.contains("@IODispatcher"))
        assertEquals("every launch goes through viewModelScope", Regex("""\blaunch\b""").findAll(source).count(),
            Regex("""viewModelScope\.launch""").findAll(source).count())
    }
}
