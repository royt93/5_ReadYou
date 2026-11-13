package com.mckimquyen.reader.ui.page.oracle

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleOutcome
import com.mckimquyen.reader.domain.model.oracle.UserWallet
import com.mckimquyen.reader.domain.oracle.OracleBetPhase
import com.mckimquyen.reader.domain.oracle.OracleBettingPolicy
import com.mckimquyen.reader.domain.oracle.OracleEvidence
import com.mckimquyen.reader.domain.repository.OracleWalletDao
import com.mckimquyen.reader.domain.repository.PlaceBetResult
import com.mckimquyen.reader.domain.repository.SettleResult
import com.mckimquyen.reader.infrastructure.ai.OracleEvidenceFinder
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OracleEvidenceUiState {
    data object Idle : OracleEvidenceUiState
    data object Loading : OracleEvidenceUiState
    data class Ready(val items: List<OracleEvidence>) : OracleEvidenceUiState
}

data class OracleBetUiState(val phase: OracleBetPhase, val balance: Long, val bet: OracleBet?)

fun buildOracleBetUiState(card: OracleClaimCard, wallet: UserWallet?, bet: OracleBet?, now: Long): OracleBetUiState {
    val balance = wallet?.balance ?: OracleBettingPolicy.STARTING_BALANCE
    // An existing bet keeps the deadline it was placed under, even if the prediction row was re-dated later.
    val expiresAt = bet?.expiresAt ?: card.expiresAt
    return OracleBetUiState(OracleBettingPolicy.phaseFor(bet?.status, expiresAt, balance, now), balance, bet)
}

/**
 * Drives the betting part of the prediction card. The phase is recomputed when the data changes or a card is opened;
 * there is no timer, so a deadline that passes while the card is open shows at the next such moment.
 */
@HiltViewModel
class OracleBettingViewModel internal constructor(
    private val dao: OracleWalletDao,
    private val finder: OracleEvidenceFinder,
    private val io: CoroutineDispatcher,
    private val accountId: () -> Int,
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        dao: OracleWalletDao,
        finder: OracleEvidenceFinder,
        @ApplicationContext context: Context,
        @IODispatcher io: CoroutineDispatcher,
    ) : this(dao, finder, io, { context.currentAccountId }, System::currentTimeMillis)

    private val openCard = MutableStateFlow<OracleClaimCard?>(null)
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<OracleBetUiState?> = openCard
        .flatMapLatest { card ->
            if (card == null) flowOf(null)
            else combine(dao.observeWallet(), dao.observeBet(card.articleId)) { wallet, bet ->
                buildOracleBetUiState(card, wallet, bet, clock())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Hints for a bet that reached its date: loaded once per (card, phase), only while the bet awaits its result, and only
     * from the on-device finder. Any failure degrades to "nothing found".
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val evidence: StateFlow<OracleEvidenceUiState> = state
        .map { ui -> ui?.takeIf { it.phase == OracleBetPhase.AWAITING_RESULT }?.bet }
        .distinctUntilChanged { old, new -> old?.articleId == new?.articleId }
        .flatMapLatest { bet ->
            if (bet == null) flowOf(OracleEvidenceUiState.Idle)
            else flow {
                emit(OracleEvidenceUiState.Loading)
                emit(OracleEvidenceUiState.Ready(guarded { finder.find(accountId(), bet) }.orEmpty()))
            }.flowOn(io)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OracleEvidenceUiState.Idle)

    fun open(card: OracleClaimCard) {
        _failed.value = false
        openCard.value = card
        viewModelScope.launch(io) { guarded { dao.ensureWalletAndGrant(clock()) } }
    }

    fun close() {
        openCard.value = null
        _failed.value = false
    }

    fun placeBet(side: OracleBetSide, stake: Int) {
        val card = openCard.value ?: return
        viewModelScope.launch(io) {
            val placed = guarded { dao.placeBet(card.articleId, card.question, card.expiresAt, side, stake, clock()) }
            _failed.value = placed !is PlaceBetResult.Placed
        }
    }

    fun settle(outcome: OracleOutcome) {
        val card = openCard.value ?: return
        viewModelScope.launch(io) {
            val settled = guarded { dao.settleBet(card.articleId, outcome, clock()) }
            _failed.value = settled !is SettleResult.Settled
        }
    }

    /** Any failure becomes `null` (shown as one generic message); cancellation is never swallowed. */
    private suspend fun <T> guarded(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
