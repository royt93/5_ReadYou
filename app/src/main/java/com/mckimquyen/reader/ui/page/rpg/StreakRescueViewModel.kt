package com.mckimquyen.reader.ui.page.rpg

import com.mckimquyen.reader.infrastructure.android.AppLog
import com.mckimquyen.reader.infrastructure.android.RewardedGate
import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.rpg.StreakRescueOffer
import com.mckimquyen.reader.domain.model.rpg.StreakRescuePolicy
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface StreakRescueUiState {
    data object Idle : StreakRescueUiState

    /** [adFailed] is a flag, not a message, so the UI resolves the localized text itself. */
    data class Prompt(
        val offer: StreakRescueOffer,
        val isLoadingAd: Boolean = false,
        val adFailed: Boolean = false,
    ) : StreakRescueUiState
}

private const val TAG = "StreakRescue"

private fun showRewardedAd(activity: Activity, onResult: (Boolean) -> Unit) {
    RewardedGate.shared.request(activity, onResult)
}

/**
 * Offers to save or restart a broken reading streak when the app opens (RPG-03). The prompt appears at most once per
 * UTC day. Every state change re-validates eligibility in [BrainRpgRepository], because the ad callback is asynchronous.
 */
@HiltViewModel
class StreakRescueViewModel internal constructor(
    private val repository: BrainRpgRepository,
    private val promptDayStore: StreakRescuePromptDayStore,
    @Suppress("unused") private val defaultDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
    private val adLauncher: (Activity, (Boolean) -> Unit) -> Unit,
) : ViewModel() {

    /** Hilt entry point: Hilt cannot provide function-typed parameters, so the clock and ad launcher are defaulted here. */
    @Inject constructor(
        repository: BrainRpgRepository,
        promptDayStore: StreakRescuePromptDayStore,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(repository, promptDayStore, defaultDispatcher, ioDispatcher, System::currentTimeMillis, ::showRewardedAd)

    private val _state = MutableStateFlow<StreakRescueUiState>(StreakRescueUiState.Idle)
    val state: StateFlow<StreakRescueUiState> = _state.asStateFlow()

    fun checkOnStartup() {
        AppLog.event("streak", "check-on-startup")
        viewModelScope.launch {
            try {
                val today = StreakRescuePolicy.epochDay(clock())
                val progress = withContext(ioDispatcher) { repository.getProgress() }
                val lastPromptDay = withContext(ioDispatcher) { promptDayStore.readLastPromptDay() }
                val offer = StreakRescuePolicy.evaluate(
                    streakDays = progress.streakDays,
                    lastReadEpochDay = progress.lastReadEpochDay,
                    shieldActive = progress.streakShieldActive,
                    todayEpochDay = today,
                    lastPromptEpochDay = lastPromptDay,
                ) ?: return@launch
                // Not written here: the mark used to be saved before the dialog showed, so if the process died or the screen was recreated first, the user was not offered
                // the rescue again for the rest of the day. It is saved once the user has answered (see markAnsweredToday).
                _state.value = StreakRescueUiState.Prompt(offer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed check must never block app start; the dialog simply does not appear.
                Log.w(TAG, "[checkOnStartup] skipped: $e")
            }
        }
    }

    fun onRescueWithAd(activity: Activity) {
        val current = _state.value as? StreakRescueUiState.Prompt ?: return
        if (current.isLoadingAd) return
        _state.value = current.copy(isLoadingAd = true, adFailed = false)
        adLauncher(activity) { earned ->
            viewModelScope.launch {
                val rescued = earned && withContext(ioDispatcher) { repository.rescueBrokenStreak(clock()) }
                _state.value = if (rescued) {
                    markAnsweredToday()
                    StreakRescueUiState.Idle
                } else {
                    current.copy(isLoadingAd = false, adFailed = true)
                }
            }
        }
    }

    fun onRestartFromScratch() {
        AppLog.event("streak", "restart")
        if ((_state.value as? StreakRescueUiState.Prompt)?.isLoadingAd == true) return
        viewModelScope.launch {
            withContext(ioDispatcher) { repository.resetBrokenStreak(clock()) }
            markAnsweredToday()
            _state.value = StreakRescueUiState.Idle
        }
    }

    fun onDismissLater() {
        AppLog.event("streak", "dismiss-later")
        if ((_state.value as? StreakRescueUiState.Prompt)?.isLoadingAd == true) return
        viewModelScope.launch { markAnsweredToday() }
        _state.value = StreakRescueUiState.Idle
    }

    /** Remember that today's question has been answered, so it is not asked again today. */
    private suspend fun markAnsweredToday() {
        try {
            withContext(ioDispatcher) { promptDayStore.writeLastPromptDay(StreakRescuePolicy.epochDay(clock())) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[markAnsweredToday] not saved: $e")
        }
    }
}
