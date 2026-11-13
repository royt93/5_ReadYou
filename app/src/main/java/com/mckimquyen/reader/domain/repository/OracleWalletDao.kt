package com.mckimquyen.reader.domain.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.model.oracle.UserWallet
import com.mckimquyen.reader.domain.oracle.BetRejection
import com.mckimquyen.reader.domain.oracle.OracleBettingPolicy
import kotlinx.coroutines.flow.Flow

sealed interface PlaceBetResult {
    data class Placed(val bet: OracleBet, val balance: Long) : PlaceBetResult
    data class Rejected(val reason: BetRejection) : PlaceBetResult
}

sealed interface SettleResult {
    data class Settled(val bet: OracleBet, val payout: Long, val balance: Long) : SettleResult
    data object NotSettleable : SettleResult
}

/**
 * Every money-like change (grant, stake, payout, refund) is one `@Transaction` that re-checks its own preconditions
 * inside the transaction, so a double tap or a repeated call can never charge or pay twice (pattern of
 * BrainRpgDao.claimAndApplyReward).
 */
@Dao
abstract class OracleWalletDao {

    @Query("SELECT * FROM user_wallet WHERE id = 1")
    abstract fun observeWallet(): Flow<UserWallet?>

    @Query("SELECT * FROM oracle_bet WHERE articleId = :articleId")
    abstract fun observeBet(articleId: String): Flow<OracleBet?>

    @Query("SELECT * FROM user_wallet WHERE id = 1")
    abstract suspend fun wallet(): UserWallet?

    @Query("SELECT * FROM oracle_bet WHERE articleId = :articleId")
    abstract suspend fun betFor(articleId: String): OracleBet?

    /** Open bets whose deadline has passed, oldest deadline first. Read-only; used to remind the user (ORACLE-03). */
    @Query("SELECT * FROM oracle_bet WHERE status = 'OPEN' AND expiresAt <= :nowMillis ORDER BY expiresAt ASC, id ASC")
    abstract suspend fun dueOpenBets(nowMillis: Long): List<OracleBet>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertWalletIfAbsent(wallet: UserWallet): Long

    @Query("UPDATE user_wallet SET balance = :balance, lastGrantEpochDay = :lastGrantEpochDay WHERE id = 1")
    protected abstract suspend fun updateWallet(balance: Long, lastGrantEpochDay: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertBetIfAbsent(bet: OracleBet): Long

    @Query("UPDATE oracle_bet SET status = :status, settledAt = :settledAt WHERE id = :id AND status = 'OPEN'")
    protected abstract suspend fun closeOpenBet(id: Long, status: OracleBetStatus, settledAt: Long): Int

    @Query("SELECT * FROM oracle_bet WHERE status = 'OPEN'")
    protected abstract suspend fun openBets(): List<OracleBet>

    @Transaction
    open suspend fun ensureWalletAndGrant(nowMillis: Long): UserWallet {
        val today = OracleBettingPolicy.epochDayUtc(nowMillis)
        insertWalletIfAbsent(UserWallet(balance = OracleBettingPolicy.STARTING_BALANCE, lastGrantEpochDay = today))
        val current = requireNotNull(wallet()) { "wallet row must exist right after insertWalletIfAbsent" }
        val granted = OracleBettingPolicy.dailyGrant(current.balance, current.lastGrantEpochDay, today)
        if (granted.balance == current.balance && granted.lastGrantEpochDay == current.lastGrantEpochDay) return current
        updateWallet(granted.balance, granted.lastGrantEpochDay)
        return current.copy(balance = granted.balance, lastGrantEpochDay = granted.lastGrantEpochDay)
    }

    @Transaction
    open suspend fun placeBet(
        articleId: String,
        question: String,
        expiresAt: Long,
        side: OracleBetSide,
        stake: Int,
        nowMillis: Long,
    ): PlaceBetResult {
        val wallet = ensureWalletAndGrant(nowMillis)
        val rejection = OracleBettingPolicy.checkBet(stake, wallet.balance, expiresAt, nowMillis, betFor(articleId) != null)
        if (rejection != null) return PlaceBetResult.Rejected(rejection)
        val bet = OracleBet(
            articleId = articleId, question = question, expiresAt = expiresAt, side = side, stake = stake,
            status = OracleBetStatus.OPEN, placedAt = nowMillis,
        )
        // The unique index is the last line of defence; a lost race comes back as -1 and is reported as already bet.
        val id = insertBetIfAbsent(bet)
        if (id == -1L) return PlaceBetResult.Rejected(BetRejection.ALREADY_BET)
        val balance = wallet.balance - stake
        updateWallet(balance, wallet.lastGrantEpochDay)
        return PlaceBetResult.Placed(bet.copy(id = id), balance)
    }

    @Transaction
    open suspend fun settleBet(articleId: String, outcome: OracleOutcome, nowMillis: Long): SettleResult {
        val bet = betFor(articleId) ?: return SettleResult.NotSettleable
        if (!OracleBettingPolicy.canSettle(bet.status, bet.expiresAt, nowMillis)) return SettleResult.NotSettleable
        val settlement = OracleBettingPolicy.settle(bet.side, bet.stake, outcome)
        if (closeOpenBet(bet.id, settlement.status, nowMillis) == 0) return SettleResult.NotSettleable
        val wallet = requireNotNull(wallet()) { "a bet cannot exist without its wallet" }
        val balance = wallet.balance + settlement.payout
        updateWallet(balance, wallet.lastGrantEpochDay)
        return SettleResult.Settled(bet.copy(status = settlement.status, settledAt = nowMillis), settlement.payout, balance)
    }

    /** Returns how many stakes were given back. Safe to call on every sync. */
    @Transaction
    open suspend fun autoRefundStale(nowMillis: Long): Int {
        var refunded = 0
        for (bet in openBets()) {
            if (!OracleBettingPolicy.shouldAutoRefund(bet.status, bet.expiresAt, nowMillis)) continue
            if (closeOpenBet(bet.id, OracleBetStatus.REFUNDED, nowMillis) == 0) continue
            val wallet = wallet() ?: continue
            updateWallet(wallet.balance + bet.stake, wallet.lastGrantEpochDay)
            refunded++
        }
        return refunded
    }
}
