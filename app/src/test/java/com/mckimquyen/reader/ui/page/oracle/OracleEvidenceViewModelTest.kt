package com.mckimquyen.reader.ui.page.oracle

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.oracle.OracleEvidence
import com.mckimquyen.reader.infrastructure.ai.OracleEvidenceFinder
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleEvidenceViewModelTest {

    private lateinit var db: AndroidDatabase
    private val finder: OracleEvidenceFinder = mockk()
    private val day = 86_400_000L
    private var nowMs = 100 * day
    private val card get() = OracleClaimCard("a", "Will it ship?", nowMs + 5 * day, "Release notes.")
    private val evidence = OracleEvidence("e1", "Ship date confirmed", "Wire", 120 * day)
    private lateinit var vm: OracleBettingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        coEvery { finder.find(any(), any()) } returns listOf(evidence)
        vm = OracleBettingViewModel(db.oracleWalletDao(), finder, Dispatchers.Unconfined, accountId = { 7 }, clock = { nowMs })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun <T> awaitReal(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.Default) { withTimeout(5_000) { block() } } }

    private fun evidenceWhere(p: (OracleEvidenceUiState) -> Boolean) = awaitReal { vm.evidence.first(p) }

    private fun stateWhere(p: (OracleBetUiState) -> Boolean) = awaitReal { vm.state.first { it != null && p(it) }!! }

    private fun placeBetAndLetItExpire() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        vm.placeBet(OracleBetSide.YES, 100)
        stateWhere { it.bet != null && it.balance == 900L }
        nowMs += 6 * day
        vm.close(); vm.open(card)
        stateWhere { it.phase == com.mckimquyen.reader.domain.oracle.OracleBetPhase.AWAITING_RESULT }
    }

    @Test fun nothingOpen_isIdle() = assertEquals(OracleEvidenceUiState.Idle, vm.evidence.value)

    @Test fun whileTheCardCanStillBeBetOn_noSearchIsMade() {
        vm.open(card)
        stateWhere { it.balance == 1000L }
        assertEquals(OracleEvidenceUiState.Idle, evidenceWhere { true })
        coVerify(exactly = 0) { finder.find(any(), any()) }
    }

    @Test fun whenTheBetIsDue_theEvidenceIsLoaded_forTheRightAccountAndBet() {
        placeBetAndLetItExpire()
        val ready = evidenceWhere { it is OracleEvidenceUiState.Ready } as OracleEvidenceUiState.Ready
        assertEquals(listOf(evidence), ready.items)
        coVerify { finder.find(7, match { it.articleId == "a" && it.question == "Will it ship?" }) }
    }

    @Test fun whileSearching_itShowsLoading() {
        val gate = CompletableDeferred<List<OracleEvidence>>()
        coEvery { finder.find(any(), any()) } coAnswers { gate.await() }
        placeBetAndLetItExpire()
        assertEquals(OracleEvidenceUiState.Loading, evidenceWhere { it == OracleEvidenceUiState.Loading })
        gate.complete(listOf(evidence))
        assertTrue(evidenceWhere { it is OracleEvidenceUiState.Ready } is OracleEvidenceUiState.Ready)
    }

    @Test fun nothingFound_isAnEmptyReady_notAnError() {
        coEvery { finder.find(any(), any()) } returns emptyList()
        placeBetAndLetItExpire()
        assertEquals(OracleEvidenceUiState.Ready(emptyList()), evidenceWhere { it is OracleEvidenceUiState.Ready })
    }

    @Test fun aThrowingFinder_degradesToAnEmptyReady() {
        coEvery { finder.find(any(), any()) } throws IllegalStateException("boom")
        placeBetAndLetItExpire()
        assertEquals(OracleEvidenceUiState.Ready(emptyList()), evidenceWhere { it is OracleEvidenceUiState.Ready })
    }

    @Test fun closingTheCard_goesBackToIdle() {
        placeBetAndLetItExpire()
        evidenceWhere { it is OracleEvidenceUiState.Ready }
        vm.close()
        assertEquals(OracleEvidenceUiState.Idle, evidenceWhere { it == OracleEvidenceUiState.Idle })
    }

    @Test fun settlingTheBet_removesTheEvidenceSection() {
        placeBetAndLetItExpire()
        evidenceWhere { it is OracleEvidenceUiState.Ready }
        vm.settle(com.mckimquyen.reader.domain.model.oracle.OracleOutcome.UNCLEAR)
        assertEquals(OracleEvidenceUiState.Idle, evidenceWhere { it == OracleEvidenceUiState.Idle })
    }

    @Test fun aWalletChange_whileTheCardIsOpen_doesNotSearchAgain() {
        placeBetAndLetItExpire()
        evidenceWhere { it is OracleEvidenceUiState.Ready }
        // The state flow re-emits (new balance) for the very same bet; that must not restart the search.
        // A raw SQL write would not notify Room's flows, so go through the DAO: the next UTC day grants 100 points.
        runBlocking { db.oracleWalletDao().ensureWalletAndGrant(nowMs + day) }
        stateWhere { it.balance == 1000L }
        awaitReal { kotlinx.coroutines.delay(300) }
        evidenceWhere { it is OracleEvidenceUiState.Ready }
        coVerify(exactly = 1) { finder.find(any(), any()) }
    }

    @Test fun theSameBetIsSearchedOnlyOnce_whileTheCardStaysOpen() {
        placeBetAndLetItExpire()
        evidenceWhere { it is OracleEvidenceUiState.Ready }
        vm.failed.value // touching another flow must not trigger another search
        coVerify(exactly = 1) { finder.find(any(), any()) }
    }
}
