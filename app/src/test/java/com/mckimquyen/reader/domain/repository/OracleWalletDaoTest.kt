package com.mckimquyen.reader.domain.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.oracle.BetRejection
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleWalletDaoTest {

    private lateinit var db: AndroidDatabase
    private lateinit var dao: OracleWalletDao

    private val day = 86_400_000L
    private val now = 100 * day + 3_600_000L // 01:00 UTC of epoch day 100
    private val expires = now + 10 * day

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.oracleWalletDao()
    }

    @After
    fun tearDown() = db.close()

    private fun balance(): Long = runBlocking { dao.wallet()?.balance ?: -1L }

    private fun bet(article: String = "a", stake: Int = 100, side: OracleBetSide = OracleBetSide.YES, exp: Long = expires, at: Long = now) =
        runBlocking { dao.placeBet(article, "Q-$article?", exp, side, stake, at) }

    // --- wallet

    @Test
    fun firstUse_createsTheWalletWithTheStartingBalance() = runBlocking {
        val w = dao.ensureWalletAndGrant(now)
        assertEquals(1000L, w.balance)
        assertEquals(100L, w.lastGrantEpochDay)
        assertEquals(1, db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM user_wallet").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun sameDay_secondCallGrantsNothing() = runBlocking {
        dao.ensureWalletAndGrant(now)
        assertEquals(1000L, dao.ensureWalletAndGrant(now + 3_600_000L).balance)
    }

    @Test
    fun nextDay_grantsOnce_thenNothingMoreThatDay() = runBlocking {
        dao.ensureWalletAndGrant(now)
        assertEquals(1100L, dao.ensureWalletAndGrant(now + day).balance)
        assertEquals(1100L, dao.ensureWalletAndGrant(now + day + 1000).balance)
    }

    @Test
    fun grantStopsAtTheCap() = runBlocking {
        dao.ensureWalletAndGrant(now)
        var t = now
        repeat(15) { t += day; dao.ensureWalletAndGrant(t) }
        assertEquals(2000L, balance())
    }

    // --- placeBet

    @Test
    fun placeBet_takesThePointsAndWritesTheBet_inOneStep() = runBlocking {
        val r = bet(stake = 100)
        assertTrue(r is PlaceBetResult.Placed)
        r as PlaceBetResult.Placed
        assertEquals(900L, r.balance)
        assertEquals(900L, balance())
        val b = dao.betFor("a")!!
        assertEquals(OracleBetStatus.OPEN, b.status)
        assertEquals(100, b.stake)
        assertEquals(OracleBetSide.YES, b.side)
        assertEquals("Q-a?", b.question)
        assertEquals(expires, b.expiresAt)
        assertEquals(now, b.placedAt)
        assertNull(b.settledAt)
    }

    @Test
    fun placeBet_rejections_changeNothing() = runBlocking {
        dao.ensureWalletAndGrant(now)
        assertEquals(BetRejection.BAD_STAKE, (bet(stake = 75) as PlaceBetResult.Rejected).reason)
        assertEquals(BetRejection.EXPIRED, (bet(exp = now) as PlaceBetResult.Rejected).reason)
        assertEquals(1000L, balance())
        assertNull(dao.betFor("a"))
    }

    @Test
    fun placeBet_insufficientBalance_isRejected() = runBlocking {
        bet(article = "a1", stake = 500); bet(article = "a2", stake = 500) // 1000 -> 0
        assertEquals(0L, balance())
        assertEquals(BetRejection.INSUFFICIENT_BALANCE, (bet(article = "a3", stake = 50) as PlaceBetResult.Rejected).reason)
        assertEquals(0L, balance())
    }

    @Test
    fun placeBet_secondBetOnTheSameArticle_isRejected_andChargesNothing() = runBlocking {
        bet(stake = 100)
        assertEquals(BetRejection.ALREADY_BET, (bet(stake = 50, side = OracleBetSide.NO) as PlaceBetResult.Rejected).reason)
        assertEquals(900L, balance())
        assertEquals(OracleBetSide.YES, dao.betFor("a")!!.side)
    }

    @Test
    fun placeBet_concurrentDoubleTap_chargesExactlyOnce() = runBlocking {
        dao.ensureWalletAndGrant(now)
        val results = (1..8).map { async(Dispatchers.IO) { bet(stake = 100) } }.awaitAll()
        assertEquals(1, results.count { it is PlaceBetResult.Placed })
        assertEquals(900L, balance())
    }

    // --- settleBet

    @Test
    fun settle_beforeTheDeadline_isRefused() = runBlocking {
        bet()
        assertEquals(SettleResult.NotSettleable, dao.settleBet("a", OracleOutcome.YES, expires - 1))
        assertEquals(OracleBetStatus.OPEN, dao.betFor("a")!!.status)
        assertEquals(900L, balance())
    }

    @Test
    fun settle_correctSide_paysDouble() = runBlocking {
        bet(stake = 100, side = OracleBetSide.YES)
        val r = dao.settleBet("a", OracleOutcome.YES, expires) as SettleResult.Settled
        assertEquals(200L, r.payout)
        assertEquals(1100L, r.balance)
        assertEquals(1100L, balance())
        val b = dao.betFor("a")!!
        assertEquals(OracleBetStatus.WON, b.status)
        assertEquals(expires, b.settledAt)
    }

    @Test
    fun settle_wrongSide_losesTheStake() = runBlocking {
        bet(stake = 100, side = OracleBetSide.YES)
        val r = dao.settleBet("a", OracleOutcome.NO, expires) as SettleResult.Settled
        assertEquals(0L, r.payout)
        assertEquals(900L, balance())
        assertEquals(OracleBetStatus.LOST, dao.betFor("a")!!.status)
    }

    @Test
    fun settle_unclear_refundsTheStake() = runBlocking {
        bet(stake = 100)
        dao.settleBet("a", OracleOutcome.UNCLEAR, expires)
        assertEquals(1000L, balance())
        assertEquals(OracleBetStatus.REFUNDED, dao.betFor("a")!!.status)
    }

    @Test
    fun settle_twice_paysOnce() = runBlocking {
        bet(stake = 100)
        dao.settleBet("a", OracleOutcome.YES, expires)
        assertEquals(SettleResult.NotSettleable, dao.settleBet("a", OracleOutcome.YES, expires + 1))
        assertEquals(1100L, balance())
    }

    @Test
    fun settle_concurrentDoubleTap_paysExactlyOnce() = runBlocking {
        bet(stake = 100)
        val results = (1..8).map { async(Dispatchers.IO) { dao.settleBet("a", OracleOutcome.YES, expires) } }.awaitAll()
        assertEquals(1, results.count { it is SettleResult.Settled })
        assertEquals(1100L, balance())
    }

    @Test
    fun settle_withoutABet_isRefused() = runBlocking {
        assertEquals(SettleResult.NotSettleable, dao.settleBet("nope", OracleOutcome.YES, expires))
    }

    @Test
    fun settledBalanceIsNotCutByTheCap() = runBlocking {
        dao.ensureWalletAndGrant(now)
        var t = now
        repeat(15) { t += day; dao.ensureWalletAndGrant(t) } // 2000
        val placed = dao.placeBet("big", "Q?", t + 10 * day, OracleBetSide.YES, 500, t)
        assertTrue(placed is PlaceBetResult.Placed)
        val r = dao.settleBet("big", OracleOutcome.YES, t + 10 * day) as SettleResult.Settled
        assertEquals(2500L, r.balance)
    }

    // --- autoRefundStale

    @Test
    fun autoRefund_returnsStakesOfBetsOpenFor30DaysPastTheDeadline_only() = runBlocking {
        bet(article = "stale", stake = 100, exp = now + day)
        bet(article = "fresh", stake = 50, exp = now + day)
        dao.settleBet("fresh", OracleOutcome.YES, now + day) // settled, must not be touched again
        val t = now + day + 30 * day
        val n = dao.autoRefundStale(t)
        assertEquals(1, n)
        assertEquals(OracleBetStatus.REFUNDED, dao.betFor("stale")!!.status)
        assertEquals(OracleBetStatus.WON, dao.betFor("fresh")!!.status)
        // 1000 -100 -50 = 850; fresh WON pays 100 -> 950; stale refunded 100 -> 1050
        assertEquals(1050L, balance())
    }

    @Test
    fun autoRefund_justBeforeTheMark_doesNothing() = runBlocking {
        bet(stake = 100, exp = now + day)
        assertEquals(0, dao.autoRefundStale(now + day + 30 * day - 1))
        assertEquals(OracleBetStatus.OPEN, dao.betFor("a")!!.status)
        assertEquals(900L, balance())
    }

    @Test
    fun autoRefund_isIdempotent() = runBlocking {
        bet(stake = 100, exp = now + day)
        val t = now + day + 30 * day
        dao.autoRefundStale(t)
        assertEquals(0, dao.autoRefundStale(t + day))
        assertEquals(1000L, balance())
    }

    // --- observers and independence

    @Test
    fun observers_emitTheCurrentRows() = runBlocking {
        assertNull(dao.observeWallet().first())
        assertNull(dao.observeBet("a").first())
        bet()
        assertEquals(900L, dao.observeWallet().first()!!.balance)
        assertNotNull(dao.observeBet("a").first())
    }

    @Test
    fun betsDoNotDependOnOracleMarketRows() = runBlocking {
        // The prediction row is deleted by the ORACLE-01 cleanup; the bet and its points must remain.
        bet(stake = 100)
        db.openHelper.writableDatabase.execSQL("DELETE FROM oracle_market")
        assertNotNull(dao.betFor("a"))
        assertEquals(900L, balance())
    }

    // --- atomicity: a wallet update that fails half way must undo everything done before it in the same call

    private fun failWalletUpdates() = db.openHelper.writableDatabase.execSQL(
        "CREATE TRIGGER fail_wallet_update BEFORE UPDATE ON user_wallet BEGIN SELECT RAISE(ABORT, 'boom'); END"
    )

    private fun allowWalletUpdates() = db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_wallet_update")

    @Test
    fun placeBet_isAllOrNothing_whenTheWalletUpdateFails() = runBlocking {
        dao.ensureWalletAndGrant(now)
        failWalletUpdates()
        val failed = runCatching { dao.placeBet("a", "Q?", expires, OracleBetSide.YES, 100, now) }.isFailure
        allowWalletUpdates()
        assertTrue("the failing update must surface", failed)
        assertNull("the bet row must be rolled back with the failed charge", dao.betFor("a"))
        assertEquals(1000L, balance())
    }

    @Test
    fun settleBet_isAllOrNothing_whenTheWalletUpdateFails() = runBlocking {
        bet(stake = 100)
        failWalletUpdates()
        val failed = runCatching { dao.settleBet("a", OracleOutcome.YES, expires) }.isFailure
        allowWalletUpdates()
        assertTrue(failed)
        assertEquals("the bet must not be closed without its payout", OracleBetStatus.OPEN, dao.betFor("a")!!.status)
        assertEquals(900L, balance())
        // and it can still be settled once the wallet works again
        assertTrue(dao.settleBet("a", OracleOutcome.YES, expires) is SettleResult.Settled)
        assertEquals(1100L, balance())
    }

    @Test
    fun autoRefundStale_isAllOrNothing_whenTheWalletUpdateFails() = runBlocking {
        bet(stake = 100, exp = now + day)
        failWalletUpdates()
        val failed = runCatching { dao.autoRefundStale(now + day + 30 * day) }.isFailure
        allowWalletUpdates()
        assertTrue(failed)
        assertEquals("the stake must not be marked refunded without being returned", OracleBetStatus.OPEN, dao.betFor("a")!!.status)
        assertEquals(900L, balance())
        assertEquals(1, dao.autoRefundStale(now + day + 30 * day))
        assertEquals(1000L, balance())
    }
}
