package com.mckimquyen.reader.ui.page.home.read.bounty

import com.mckimquyen.reader.domain.model.bounty.BountyInvestigationResult

enum class BountyInvestigationPhase { SCANNING, RESULTS, ERROR }

/** Trạng thái phần AI tuỳ chọn (chỉ dùng key Gemini riêng của người dùng). */
sealed interface BountyAiState {
    data object Idle : BountyAiState
    data object Loading : BountyAiState
    data class Ready(val text: String) : BountyAiState
    data class Failed(val reason: BountyAiFailure) : BountyAiState
}

enum class BountyAiFailure { NO_KEY, RATE_LIMITED, INVALID_KEY, NETWORK, EMPTY, UNKNOWN }

data class BountyInvestigationState(
    val phase: BountyInvestigationPhase = BountyInvestigationPhase.SCANNING,
    val query: String = "",
    val result: BountyInvestigationResult? = null,
    val canUseAiContext: Boolean = false,
    val ai: BountyAiState = BountyAiState.Idle,
)
