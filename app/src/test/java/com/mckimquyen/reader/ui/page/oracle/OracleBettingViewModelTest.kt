package com.mckimquyen.reader.ui.page.oracle

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import com.mckimquyen.reader.domain.repository.OracleWalletDao
import com.mckimquyen.reader.infrastructure.ai.OracleEvidenceFinder
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleBettingViewModelTest {

    private lateinit var db: AndroidDatabase
    // The evidence part has its own tests; these ones are about the wallet and the bet.
    private val NoEvidenceFinder: OracleEvidenceFinder = mockk { coEvery { find(any(), any()) } returns emptyList() }
    private val day = 86_400_000L
    private var nowMs = 100 * day + 3_600_000L
    private val expires get() = nowMs + 5 * day
    private val card get() = OracleClaimCard("a", "Will it ship?", expires, "Release notes.")
    private lateinit var vm: OracleBettingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        vm = OracleBettingViewModel(db.oracleWalletDao(), NoEvidenceFinder, Dispatchers.Unconfined, accountId = { 1 }, clock = { nowMs })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private companion object {
        const val WALLET_OPEN_BOOM = "wallet-open-boom-only-this-test"
    }

    private fun <T> awaitReal(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.Default) { withTimeout(5_000) { block() } } }

    private fun stateWhere(p: (OracleBetUiState) -> Boolean): OracleBetUiState = awaitReal { vm.state.first { it != null && p(it) }!! }

    // Room runs a transaction on its own executor, so the result of placeBet/settle arrives a moment later.
    private fun failedBecomes(value: Boolean) = awaitReal { vm.failed.first { it == value } }

    @Test fun nothingOpen_meansNoState() {
        assertNull(vm.state.value)
        assertFalse(vm.failed.value)
    }

    @Test fun open_createsTheWallet_andOffersTheBet() {
        vm.open(card)
        val s = stateWhere { it.balance == 1000L }
        assertEquals(OracleBetPhase.CAN_BET, s.phase)
        assertNull(s.bet)
    }

    @Test fun open_onANewDay_grantsTheDailyPoints() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        nowMs += day
        vm.close()
        vm.open(card)
        assertEquals(1100L, stateWhere { it.balance == 1100L }.balance)
    }

    @Test fun placeBet_takesThePoints_andShowsTheOpenBet() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.YES, 100)
        // The wallet and the bet are two Room flows that refresh separately, so a frame can show the new bet with the
        // old balance; wait for the settled picture.
        val s = stateWhere { it.phase == OracleBetPhase.BET_OPEN && it.balance == 900L }
        assertEquals(900L, s.balance)
        assertEquals(100, s.bet!!.stake)
        assertEquals(OracleBetSide.YES, s.bet!!.side)
        assertFalse(vm.failed.value)
    }

    @Test fun placeBet_withABadStake_failsAndChargesNothing() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.NO, 75)
        assertTrue(failedBecomes(true))
        assertEquals(1000L, stateWhere { true }.balance)
    }

    @Test fun aSuccessfulBetClearsEarlierFailure() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.NO, 75)
        assertTrue(failedBecomes(true))
        vm.placeBet(OracleBetSide.NO, 50)
        stateWhere { it.phase == OracleBetPhase.BET_OPEN }
        assertFalse(failedBecomes(false))
    }

    @Test fun settle_afterTheDeadline_paysAndShowsTheResult() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.YES, 100)
        stateWhere { it.phase == OracleBetPhase.BET_OPEN }
        nowMs += 6 * day
        vm.close(); vm.open(card)
        stateWhere { it.phase == OracleBetPhase.AWAITING_RESULT }
        vm.settle(OracleOutcome.YES)
        val s = stateWhere { it.phase == OracleBetPhase.SETTLED && it.balance == 1200L }
        assertEquals(OracleBetStatus.WON, s.bet!!.status)
        // 1000 start, -100 stake = 900; reopening on a later day grants +100 = 1000; the win pays 2 x 100 = +200.
        assertEquals(1200L, s.balance)
    }

    @Test fun settle_beforeTheDeadline_failsAndPaysNothing() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.YES, 100)
        stateWhere { it.phase == OracleBetPhase.BET_OPEN }
        vm.settle(OracleOutcome.YES)
        assertTrue(failedBecomes(true))
        assertEquals(OracleBetStatus.OPEN, stateWhere { true }.bet!!.status)
    }

    @Test fun close_clearsTheState_andTheFailure() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.NO, 75)
        failedBecomes(true)
        vm.close()
        // Room's change notification for the bet can still be in flight on another thread when failed turns true, so the state
        // reaches null a moment after close() (measured 2026-11-18: 2 of 300 runs read the old state right away, all settled to null).
        awaitReal { vm.state.first { it == null } }
        assertNull(vm.state.value)
        assertFalse("closing the card forgets an earlier failure", vm.failed.value)
    }

    @Test fun openingAnotherPrediction_switchesTheBetShown() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.YES, 100)
        stateWhere { it.phase == OracleBetPhase.BET_OPEN }
        vm.open(OracleClaimCard("b", "Other?", expires, "c"))
        val s = stateWhere { it.bet == null && it.balance == 900L }
        assertEquals(OracleBetPhase.CAN_BET, s.phase)
    }

    @Test fun aWalletErrorWhileOpening_doesNotCrash_andDoesNotRaiseTheFailureFlag() {
        val broken = mockk<OracleWalletDao>()
        every { broken.observeWallet() } returns emptyFlow()
        every { broken.observeBet(any()) } returns emptyFlow()
        coEvery { broken.ensureWalletAndGrant(any()) } throws IllegalStateException(WALLET_OPEN_BOOM)
        val uncaught = mutableListOf<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        // A coroutine that throws has no caller to crash: the error would reach the thread's handler and go unnoticed.
        Thread.setDefaultUncaughtExceptionHandler { _, e -> if (e.message == WALLET_OPEN_BOOM) uncaught += e }
        try {
            val v = OracleBettingViewModel(broken, NoEvidenceFinder, Dispatchers.Unconfined, accountId = { 1 }, clock = { nowMs })
            v.open(card)
            assertFalse("opening a card is not a failed bet", v.failed.value)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        assertTrue("a wallet error on open must be absorbed, not thrown out of the coroutine: ${uncaught.map { it.stackTraceToString().take(900) }}", uncaught.isEmpty())
    }

    @Test fun placeBetWithNothingOpen_isIgnored() {
        vm.placeBet(OracleBetSide.YES, 100)
        vm.settle(OracleOutcome.YES)
        assertFalse(vm.failed.value)
        assertNull(vm.state.value)
    }

    @Test fun aCancelledBet_isNotReportedAsAFailedBet() {
        val cancelling = mockk<OracleWalletDao>()
        every { cancelling.observeWallet() } returns emptyFlow()
        every { cancelling.observeBet(any()) } returns emptyFlow()
        coEvery { cancelling.ensureWalletAndGrant(any()) } returns com.mckimquyen.reader.domain.model.oracle.UserWallet(balance = 1000, lastGrantEpochDay = 1)
        coEvery { cancelling.placeBet(any(), any(), any(), any(), any(), any()) } throws kotlinx.coroutines.CancellationException("cancelled")
        val v = OracleBettingViewModel(cancelling, NoEvidenceFinder, Dispatchers.Unconfined, accountId = { 1 }, clock = { nowMs })
        v.open(card)
        v.placeBet(OracleBetSide.YES, 100)
        assertFalse("cancellation is not an error the user should see", v.failed.value)
    }

    @Test fun anErrorWhilePlacingABet_isReportedAsAFailedBet() {
        val broken = mockk<OracleWalletDao>()
        every { broken.observeWallet() } returns emptyFlow()
        every { broken.observeBet(any()) } returns emptyFlow()
        coEvery { broken.ensureWalletAndGrant(any()) } returns com.mckimquyen.reader.domain.model.oracle.UserWallet(balance = 1000, lastGrantEpochDay = 1)
        coEvery { broken.placeBet(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("boom")
        val v = OracleBettingViewModel(broken, NoEvidenceFinder, Dispatchers.Unconfined, accountId = { 1 }, clock = { nowMs })
        v.open(card)
        v.placeBet(OracleBetSide.YES, 100)
        assertTrue(awaitReal { v.failed.first { it } })
    }
}
