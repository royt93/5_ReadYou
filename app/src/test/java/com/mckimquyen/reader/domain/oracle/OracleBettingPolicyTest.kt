package com.mckimquyen.reader.domain.oracle

import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OracleBettingPolicyTest {

    private val day = 86_400_000L
    private val expires = 10 * day

    @Test
    fun constants_areHardCodedNumbers() {
        assertEquals(1000L, OracleBettingPolicy.STARTING_BALANCE)
        assertEquals(100L, OracleBettingPolicy.DAILY_GRANT)
        assertEquals(2000L, OracleBettingPolicy.BALANCE_CAP)
        assertEquals(listOf(50, 100, 500), OracleBettingPolicy.STAKES)
        assertEquals(50, OracleBettingPolicy.MIN_STAKE)
        assertEquals(30, OracleBettingPolicy.REFUND_AFTER_DAYS)
        assertEquals(2, OracleBettingPolicy.WIN_MULTIPLIER)
        assertEquals(86_400_000L, OracleBettingPolicy.DAY_MS)
    }

    // --- checkBet

    private fun check(stake: Int = 100, balance: Long = 1000, exp: Long = expires, now: Long = 5 * day, already: Boolean = false) =
        OracleBettingPolicy.checkBet(stake, balance, exp, now, already)

    @Test fun checkBet_validBetIsAccepted() = assertNull(check())

    @Test fun checkBet_onlyTheThreeStakesAreAllowed() {
        listOf(50, 100, 500).forEach { assertNull("$it", check(stake = it, balance = 1000)) }
        listOf(0, 1, 49, 51, 75, 200, 501, -50).forEach { assertEquals("$it", BetRejection.BAD_STAKE, check(stake = it)) }
    }

    @Test fun checkBet_balanceBoundary() {
        assertEquals(BetRejection.INSUFFICIENT_BALANCE, check(stake = 50, balance = 49))
        assertNull(check(stake = 50, balance = 50))
        assertEquals(BetRejection.INSUFFICIENT_BALANCE, check(stake = 500, balance = 499))
        assertNull(check(stake = 500, balance = 500))
    }

    @Test fun checkBet_deadlineBoundary_isExclusive() {
        assertNull(check(now = expires - 1))
        assertEquals(BetRejection.EXPIRED, check(now = expires))
        assertEquals(BetRejection.EXPIRED, check(now = expires + 1))
    }

    @Test fun checkBet_oneBetPerArticle() {
        assertEquals(BetRejection.ALREADY_BET, check(already = true))
    }

    @Test fun checkBet_precedence_badStake_thenExpired_thenAlready_thenBalance() {
        assertEquals(BetRejection.BAD_STAKE, check(stake = 75, now = expires, already = true, balance = 0))
        assertEquals(BetRejection.EXPIRED, check(now = expires, already = true, balance = 0))
        assertEquals(BetRejection.ALREADY_BET, check(already = true, balance = 0))
        assertEquals(BetRejection.INSUFFICIENT_BALANCE, check(balance = 0))
    }

    // --- dailyGrant

    @Test fun dailyGrant_sameDay_changesNothing() {
        assertEquals(WalletGrant(500, 7), OracleBettingPolicy.dailyGrant(500, 7, 7))
    }

    @Test fun dailyGrant_newDay_adds100_andMovesTheDay() {
        assertEquals(WalletGrant(600, 8), OracleBettingPolicy.dailyGrant(500, 7, 8))
    }

    @Test fun dailyGrant_missedDaysDoNotAccumulate() {
        assertEquals(WalletGrant(600, 20), OracleBettingPolicy.dailyGrant(500, 7, 20))
    }

    @Test fun dailyGrant_clockMovedBackwards_changesNothing_andKeepsTheLaterDay() {
        assertEquals(WalletGrant(500, 7), OracleBettingPolicy.dailyGrant(500, 7, 3))
    }

    @Test fun dailyGrant_capBoundary() {
        assertEquals(WalletGrant(2000, 8), OracleBettingPolicy.dailyGrant(1950, 7, 8))
        assertEquals(WalletGrant(2000, 8), OracleBettingPolicy.dailyGrant(1900, 7, 8))
        assertEquals(WalletGrant(1999, 8), OracleBettingPolicy.dailyGrant(1899, 7, 8))
    }

    @Test fun dailyGrant_atOrAboveTheCap_addsNothing_andNeverLowersTheBalance() {
        assertEquals(WalletGrant(2000, 8), OracleBettingPolicy.dailyGrant(2000, 7, 8))
        assertEquals(WalletGrant(2500, 8), OracleBettingPolicy.dailyGrant(2500, 7, 8))
    }

    // --- settle

    @Test fun settle_correctSide_paysDouble() {
        assertEquals(OracleSettlement(OracleBetStatus.WON, 200), OracleBettingPolicy.settle(OracleBetSide.YES, 100, OracleOutcome.YES))
        assertEquals(OracleSettlement(OracleBetStatus.WON, 1000), OracleBettingPolicy.settle(OracleBetSide.NO, 500, OracleOutcome.NO))
    }

    @Test fun settle_wrongSide_losesTheStake() {
        assertEquals(OracleSettlement(OracleBetStatus.LOST, 0), OracleBettingPolicy.settle(OracleBetSide.YES, 100, OracleOutcome.NO))
        assertEquals(OracleSettlement(OracleBetStatus.LOST, 0), OracleBettingPolicy.settle(OracleBetSide.NO, 50, OracleOutcome.YES))
    }

    @Test fun settle_unclear_refundsTheStake() {
        assertEquals(OracleSettlement(OracleBetStatus.REFUNDED, 50), OracleBettingPolicy.settle(OracleBetSide.YES, 50, OracleOutcome.UNCLEAR))
        assertEquals(OracleSettlement(OracleBetStatus.REFUNDED, 500), OracleBettingPolicy.settle(OracleBetSide.NO, 500, OracleOutcome.UNCLEAR))
    }

    // --- canSettle / shouldAutoRefund

    @Test fun canSettle_onlyAnOpenBetAtOrAfterTheDeadline() {
        assertFalse(OracleBettingPolicy.canSettle(OracleBetStatus.OPEN, expires, expires - 1))
        assertTrue(OracleBettingPolicy.canSettle(OracleBetStatus.OPEN, expires, expires))
        assertTrue(OracleBettingPolicy.canSettle(OracleBetStatus.OPEN, expires, expires + 99 * day))
        listOf(OracleBetStatus.WON, OracleBetStatus.LOST, OracleBetStatus.REFUNDED).forEach {
            assertFalse("$it", OracleBettingPolicy.canSettle(it, expires, expires + day))
        }
    }

    @Test fun shouldAutoRefund_after30DaysExactly() {
        assertFalse(OracleBettingPolicy.shouldAutoRefund(OracleBetStatus.OPEN, expires, expires + 30 * day - 1))
        assertTrue(OracleBettingPolicy.shouldAutoRefund(OracleBetStatus.OPEN, expires, expires + 30 * day))
        assertFalse(OracleBettingPolicy.shouldAutoRefund(OracleBetStatus.WON, expires, expires + 99 * day))
        assertFalse(OracleBettingPolicy.shouldAutoRefund(OracleBetStatus.REFUNDED, expires, expires + 99 * day))
    }

    // --- epochDayUtc

    @Test fun epochDayUtc_floorsAtMidnightUtc() {
        assertEquals(0L, OracleBettingPolicy.epochDayUtc(0))
        assertEquals(0L, OracleBettingPolicy.epochDayUtc(86_399_999))
        assertEquals(1L, OracleBettingPolicy.epochDayUtc(86_400_000))
        assertEquals(-1L, OracleBettingPolicy.epochDayUtc(-1))
    }

    // --- phaseFor

    private fun phase(status: OracleBetStatus?, now: Long, balance: Long = 1000) =
        OracleBettingPolicy.phaseFor(status, expires, balance, now)

    @Test fun phase_noBet() {
        assertEquals(OracleBetPhase.CAN_BET, phase(null, expires - 1))
        assertEquals(OracleBetPhase.CAN_BET, phase(null, expires - 1, balance = 50))
        assertEquals(OracleBetPhase.OUT_OF_POINTS, phase(null, expires - 1, balance = 49))
        assertEquals(OracleBetPhase.CLOSED_NO_BET, phase(null, expires))
        assertEquals(OracleBetPhase.CLOSED_NO_BET, phase(null, expires, balance = 0))
    }

    @Test fun phase_openBet() {
        assertEquals(OracleBetPhase.BET_OPEN, phase(OracleBetStatus.OPEN, expires - 1))
        assertEquals(OracleBetPhase.AWAITING_RESULT, phase(OracleBetStatus.OPEN, expires))
    }

    @Test fun phase_closedBet_isSettled() {
        listOf(OracleBetStatus.WON, OracleBetStatus.LOST, OracleBetStatus.REFUNDED).forEach {
            assertEquals("$it", OracleBetPhase.SETTLED, phase(it, expires - 1))
            assertEquals("$it", OracleBetPhase.SETTLED, phase(it, expires + day))
        }
    }
}
