package com.mckimquyen.reader.ui.page.statistics

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.statistics.ReadingStatisticsCalendar
import com.mckimquyen.reader.domain.model.statistics.ReadingStatsAssembler
import com.mckimquyen.reader.domain.repository.ArticleDao
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
import javax.inject.Inject

@HiltViewModel
class ReadingStatisticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val calendar: ReadingStatisticsCalendar,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<ReadingStatisticsUiState>(ReadingStatisticsUiState.Loading)
    val state: StateFlow<ReadingStatisticsUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    /** Truy vấn lại; lần gọi mới huỷ lần đang chạy để chỉ kết quả mới nhất được phát. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(ioDispatcher) {
            _state.value = try {
                val accountId = context.currentAccountId
                val total = articleDao.queryTotalRead(accountId)
                if (total == 0) {
                    ReadingStatisticsUiState.Empty
                } else {
                    val dayCounts = articleDao.queryReadingDayCounts(accountId, calendar.windowStartMillis())
                    val topFeeds = articleDao.queryTopFeeds(accountId, TOP_FEEDS_LIMIT)
                    ReadingStatisticsUiState.Content(
                        ReadingStatsAssembler.assemble(dayCounts, total, topFeeds, calendar.today())
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "load reading statistics failed", e)
                ReadingStatisticsUiState.Error(R.string.reading_stats_error)
            }
        }
    }

    private companion object {
        const val TAG = "ReadingStatisticsVM"
        const val TOP_FEEDS_LIMIT = 5
    }
}
