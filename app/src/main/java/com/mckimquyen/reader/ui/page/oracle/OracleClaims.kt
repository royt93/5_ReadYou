package com.mckimquyen.reader.ui.page.oracle

import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import com.mckimquyen.reader.domain.repository.OracleMarketDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the read-only prediction card shows; no points, odds or votes exist yet (ORACLE-02). */
data class OracleClaimCard(
    val articleId: String,
    val question: String,
    val expiresAt: Long,
    val resolutionCriteria: String,
)

fun OracleMarket.toCardOrNull(): OracleClaimCard? {
    val q = question?.takeIf { it.isNotBlank() } ?: return null
    val e = expiresAt ?: return null
    val c = resolutionCriteria?.takeIf { it.isNotBlank() } ?: return null
    return OracleClaimCard(articleId, q, e, c)
}

/** articleId -> card for articles that have a claim; empty when none. */
val LocalOracleClaims = compositionLocalOf { emptyMap<String, OracleClaimCard>() }

/** Opens the read-only claim card for an article; no-op where unavailable (previews, tests). */
val LocalOpenOracleClaim = compositionLocalOf<(String) -> Unit> { {} }

/** Claims are few (at most 5 per sync, dropped after expiry), so the lists observe them whole. */
@HiltViewModel
class OracleClaimsViewModel @Inject constructor(dao: OracleMarketDao) : ViewModel() {
    val claims: StateFlow<Map<String, OracleClaimCard>> = dao.observeClaims()
        .map { rows -> rows.mapNotNull { it.toCardOrNull() }.associateBy { it.articleId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
