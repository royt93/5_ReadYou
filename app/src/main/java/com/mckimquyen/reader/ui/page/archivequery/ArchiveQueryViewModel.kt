package com.mckimquyen.reader.ui.page.archivequery

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryDateRange
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryInputParser
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryInputResult
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryRange
import com.mckimquyen.reader.domain.archivequery.InputError
import com.mckimquyen.reader.domain.archivequery.RangeValidation
import com.mckimquyen.reader.domain.archivequery.ParsedArchiveQuery
import com.mckimquyen.reader.infrastructure.archivequery.ArchiveQueryRepository
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ArchiveQueryViewModel(
    private val context: Context,
    private val repository: ArchiveQueryRepository,
    private val ioDispatcher: CoroutineDispatcher,
    /** Fixed in tests; the zone of this clock is the zone used for months and date ranges. */
    private val clock: Clock,
) : ViewModel() {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        repository: ArchiveQueryRepository,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(context, repository, ioDispatcher, Clock.systemDefaultZone())

    private val zone: ZoneId get() = clock.zone

    private val _state = MutableStateFlow<ArchiveQueryUiState>(ArchiveQueryUiState.Idle)
    val state: StateFlow<ArchiveQueryUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var requestGeneration = 0L
    private var lastRequest: Request? = null

    fun submit(text: String, startDate: LocalDate?, endDateInclusive: LocalDate?) {
        cancelSearch()
        if (text.isBlank()) {
            lastRequest = null
            _state.value = ArchiveQueryUiState.Idle
            return
        }

        val now = Instant.now(clock)
        val parsedResult = ArchiveQueryInputParser.parse(text, now, zone)
        if (parsedResult is ArchiveQueryInputResult.Invalid) {
            lastRequest = null
            _state.value = ArchiveQueryUiState.InvalidInput(parsedResult.reason.toProblem())
            return
        }
        val parsed = (parsedResult as ArchiveQueryInputResult.Valid).value

        val range = when {
            startDate != null || endDateInclusive != null -> {
                if (startDate == null || endDateInclusive == null) {
                    lastRequest = null
                    _state.value = ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.IncompleteRange)
                    return
                }
                when (val validation = ArchiveQueryDateRange.validate(startDate, endDateInclusive, now, zone)) {
                    is RangeValidation.Valid -> validation.range
                    RangeValidation.Reversed -> {
                        lastRequest = null
                        _state.value = ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.RangeReversed)
                        return
                    }
                    RangeValidation.FutureEnd -> {
                        lastRequest = null
                        _state.value = ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.RangeInFuture)
                        return
                    }
                    RangeValidation.TooWide -> {
                        lastRequest = null
                        _state.value = ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.RangeTooWide)
                        return
                    }
                }
            }
            parsed.needsDateClarification -> {
                lastRequest = null
                _state.value = ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.NeedsDateClarification)
                return
            }
            parsed.explicitRange != null -> parsed.explicitRange
            else -> ArchiveQueryDateRange.default(now, zone)
        }

        val topic = parsed.topic.trim()
        if (topic.isBlank()) {
            lastRequest = null
            _state.value = ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.BlankTopic)
            return
        }

        val request = Request(topic, parsed.copy(topic = topic), range)
        lastRequest = request
        runRequest(request)
    }

    fun retry() {
        val request = lastRequest ?: return
        cancelSearch()
        runRequest(request)
    }

    private fun runRequest(request: Request) {
        val generation = ++requestGeneration
        _state.value = ArchiveQueryUiState.Searching(request.topic, request.range)
        searchJob = viewModelScope.launch(ioDispatcher) {
            val accountId = context.currentAccountId
            try {
                val result = repository.search(
                    accountId = accountId,
                    parsed = request.parsed,
                    range = request.range,
                    now = Instant.now(clock),
                    zone = zone,
                )
                if (generation != requestGeneration) return@launch
                if (context.currentAccountId != accountId) {
                    lastRequest = null
                    _state.value = ArchiveQueryUiState.Idle
                    return@launch
                }
                _state.value = if (result.totalRelevantArticles == 0) {
                    ArchiveQueryUiState.NoResults(request.topic, request.range)
                } else {
                    ArchiveQueryUiState.Ready(request.topic, result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (generation == requestGeneration && context.currentAccountId == accountId) {
                    _state.value = ArchiveQueryUiState.Error
                }
            }
        }
    }

    private fun cancelSearch() {
        requestGeneration++
        searchJob?.cancel()
        searchJob = null
    }

    private data class Request(
        val topic: String,
        val parsed: ParsedArchiveQuery,
        val range: ArchiveQueryRange,
    )

    private fun InputError.toProblem(): ArchiveQueryProblem = when (this) {
        InputError.Blank -> ArchiveQueryProblem.Blank
        InputError.TooLong -> ArchiveQueryProblem.TooLong
        InputError.BlankTopic -> ArchiveQueryProblem.BlankTopic
    }
}
