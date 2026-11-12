package com.mckimquyen.reader.domain.oracle

import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome

enum class BetRejection { BAD_STAKE, EXPIRED, ALREADY_BET, INSUFFICIENT_BALANCE }

enum class OracleBetPhase { CAN_BET, OUT_OF_POINTS, BET_OPEN, AWAITING_RESULT, SETTLED, CLOSED_NO_BET }

data class WalletGrant(val balance: Long, val lastGrantEpochDay: Long)

data class OracleSettlement(val status: OracleBetStatus, val payout: Long)

/**
 * Pure rules of the local prediction wallet (ORACLE-02). Virtual points only: nothing here touches money, ads or a server.
 */
object OracleBettingPolicy {
    const val STARTING_BALANCE = 1000L
    const val DAILY_GRANT = 100L
    const val BALANCE_CAP = 2000L
    const val MIN_STAKE = 50
    val STAKES = listOf(MIN_STAKE, 100, 500)
    const val REFUND_AFTER_DAYS = 30
    const val WIN_MULTIPLIER = 2
    const val DAY_MS = 86_400_000L

    /** First failing rule wins, in this order: stake, deadline, one bet per article, balance. */
    fun checkBet(stake: Int, balance: Long, expiresAt: Long, now: Long, alreadyBet: Boolean): BetRejection? = when {
        stake !in STAKES -> BetRejection.BAD_STAKE
        now >= expiresAt -> BetRejection.EXPIRED
        alreadyBet -> BetRejection.ALREADY_BET
        balance < stake -> BetRejection.INSUFFICIENT_BALANCE
        else -> null
    }

    /** One grant per UTC day; never accumulates, never goes back in time, never lowers a balance above the cap. */
    fun dailyGrant(balance: Long, lastGrantEpochDay: Long, todayEpochDay: Long): WalletGrant {
        if (todayEpochDay <= lastGrantEpochDay) return WalletGrant(balance, lastGrantEpochDay)
        val granted = if (balance >= BALANCE_CAP) balance else minOf(balance + DAILY_GRANT, BALANCE_CAP)
        return WalletGrant(granted, todayEpochDay)
    }

    fun settle(side: OracleBetSide, stake: Int, outcome: OracleOutcome): OracleSettlement {
        val won = (side == OracleBetSide.YES && outcome == OracleOutcome.YES) ||
            (side == OracleBetSide.NO && outcome == OracleOutcome.NO)
        return when {
            outcome == OracleOutcome.UNCLEAR -> OracleSettlement(OracleBetStatus.REFUNDED, stake.toLong())
            won -> OracleSettlement(OracleBetStatus.WON, stake.toLong() * WIN_MULTIPLIER)
            else -> OracleSettlement(OracleBetStatus.LOST, 0L)
        }
    }

    fun canSettle(status: OracleBetStatus, expiresAt: Long, now: Long): Boolean =
        status == OracleBetStatus.OPEN && now >= expiresAt

    fun shouldAutoRefund(status: OracleBetStatus, expiresAt: Long, now: Long): Boolean =
        status == OracleBetStatus.OPEN && now >= expiresAt + REFUND_AFTER_DAYS * DAY_MS

    fun epochDayUtc(nowMillis: Long): Long = Math.floorDiv(nowMillis, DAY_MS)

    fun phaseFor(betStatus: OracleBetStatus?, expiresAt: Long, balance: Long, now: Long): OracleBetPhase = when {
        betStatus == null -> when {
            now >= expiresAt -> OracleBetPhase.CLOSED_NO_BET
            balance < MIN_STAKE -> OracleBetPhase.OUT_OF_POINTS
            else -> OracleBetPhase.CAN_BET
        }
        betStatus == OracleBetStatus.OPEN -> if (now < expiresAt) OracleBetPhase.BET_OPEN else OracleBetPhase.AWAITING_RESULT
        else -> OracleBetPhase.SETTLED
    }
}
