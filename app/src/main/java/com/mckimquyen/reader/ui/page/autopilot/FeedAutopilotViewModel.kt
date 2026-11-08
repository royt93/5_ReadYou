package com.mckimquyen.reader.ui.page.autopilot

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotDecisionsCodec
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotEvaluation
import com.mckimquyen.reader.domain.autopilot.FeedDecisionRecord
import com.mckimquyen.reader.domain.autopilot.FeedDecisionType
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.sv.FeedAutopilotProcessor
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.feedAutopilotEnabled
import com.mckimquyen.reader.ui.ext.get
import com.mckimquyen.reader.ui.ext.put
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the Feed Autopilot screen. Nothing here deletes a feed on its own: unsubscribing needs requestUnsubscribe()
 * (which only reads the starred count and opens the dialog) followed by an explicit confirmUnsubscribe().
 */
@HiltViewModel
class FeedAutopilotViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val processor: FeedAutopilotProcessor,
    private val rssSv: RssSv,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<FeedAutopilotUiState>(FeedAutopilotUiState.Loading)
    val state: StateFlow<FeedAutopilotUiState> = _state.asStateFlow()

    private val _pendingUnsubscribe = MutableStateFlow<PendingUnsubscribe?>(null)
    val pendingUnsubscribe: StateFlow<PendingUnsubscribe?> = _pendingUnsubscribe.asStateFlow()

    private val _enabled = MutableStateFlow(context.feedAutopilotEnabled)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() = launchAnalysis()

    fun toggleEnabled(enabled: Boolean) {
        _enabled.value = enabled
        launchAnalysis(beforeAnalysis = { context.dataStore.put(DataStoreKeys.FeedAutopilotEnabled, enabled) })
    }

    /** One analysis at a time: a newer request cancels the older one so a late result cannot overwrite a newer state. */
    private fun launchAnalysis(beforeAnalysis: suspend () -> Unit = {}) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(ioDispatcher) {
            beforeAnalysis()
            _state.value = FeedAutopilotUiState.Loading
            _state.value = try {
                toUiState(processor.computeSuggestions())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                FeedAutopilotUiState.Error
            }
        }
    }

    fun snoozeFeed(feedId: String) = decide(feedId, FeedDecisionType.SNOOZE_30_DAYS)

    fun keepFeed(feedId: String) = decide(feedId, FeedDecisionType.KEEP_FOREVER)

    private fun decide(feedId: String, type: FeedDecisionType) {
        viewModelScope.launch(ioDispatcher) {
            val now = System.currentTimeMillis()
            val current = FeedAutopilotDecisionsCodec.decode(context.dataStore.get(DataStoreKeys.FeedAutopilotDecisions), now, null)
            val updated = current + (feedId to FeedDecisionRecord(type, now))
            // The card disappears first so the screen reacts at once; persisting follows.
            val remaining = removeSuggestionFromState(feedId)
            context.dataStore.put(DataStoreKeys.FeedAutopilotDecisions, FeedAutopilotDecisionsCodec.encode(updated))
            remaining?.let { context.dataStore.put(DataStoreKeys.FeedAutopilotPendingCount, it) }
        }
    }

    /** Reads how many starred articles would be lost, then opens the confirmation. It does not delete anything. */
    fun requestUnsubscribe(feedId: String) {
        viewModelScope.launch(ioDispatcher) {
            val feed = feedDao.queryById(feedId) ?: return@launch
            val starred = articleDao.queryStarredCountByFeedId(context.currentAccountId, feedId)
            _pendingUnsubscribe.value = PendingUnsubscribe(feed.id, feed.name, starred)
        }
    }

    fun cancelUnsubscribe() {
        _pendingUnsubscribe.value = null
    }

    fun confirmUnsubscribe() {
        val pending = _pendingUnsubscribe.value ?: return
        _pendingUnsubscribe.value = null
        viewModelScope.launch(ioDispatcher) {
            try {
                val feed = feedDao.queryById(pending.feedId) ?: return@launch
                rssSv.get().deleteFeed(feed)
                val remaining = removeSuggestionFromState(pending.feedId)
                clearDecision(pending.feedId)
                remaining?.let { context.dataStore.put(DataStoreKeys.FeedAutopilotPendingCount, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The feed was not (fully) removed: keep the card and tell the user instead of pretending it worked.
                _state.value = FeedAutopilotUiState.Error
            }
        }
    }

    private suspend fun clearDecision(feedId: String) {
        val now = System.currentTimeMillis()
        val current = FeedAutopilotDecisionsCodec.decode(context.dataStore.get(DataStoreKeys.FeedAutopilotDecisions), now, null)
        if (feedId in current) {
            context.dataStore.put(DataStoreKeys.FeedAutopilotDecisions, FeedAutopilotDecisionsCodec.encode(current - feedId))
        }
    }

    /** Drops one card from the visible state and returns how many remain, or null when no list is showing. */
    private fun removeSuggestionFromState(feedId: String): Int? {
        val current = _state.value as? FeedAutopilotUiState.HasSuggestions ?: return null
        val remaining = current.suggestions.filterNot { it.feedId == feedId }
        _state.value = if (remaining.isEmpty()) {
            FeedAutopilotUiState.Clean(current.windowDays)
        } else {
            current.copy(suggestions = remaining)
        }
        return remaining.size
    }

    private fun toUiState(evaluation: FeedAutopilotEvaluation): FeedAutopilotUiState = when (evaluation) {
        FeedAutopilotEvaluation.Disabled -> FeedAutopilotUiState.Disabled
        FeedAutopilotEvaluation.NotLocalAccount -> FeedAutopilotUiState.NotLocalAccount
        FeedAutopilotEvaluation.RetentionTooShort -> FeedAutopilotUiState.RetentionTooShort
        is FeedAutopilotEvaluation.Ready ->
            if (evaluation.suggestions.isEmpty()) {
                FeedAutopilotUiState.Clean(evaluation.windowDays)
            } else {
                FeedAutopilotUiState.HasSuggestions(evaluation.suggestions, evaluation.windowDays)
            }
    }
}
