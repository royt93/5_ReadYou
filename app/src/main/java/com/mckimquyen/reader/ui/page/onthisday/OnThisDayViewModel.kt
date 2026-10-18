package com.mckimquyen.reader.ui.page.onthisday

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.onthisday.OnThisDayCalendar
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
class OnThisDayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val calendar: OnThisDayCalendar,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<OnThisDayUiState>(OnThisDayUiState.Loading)
    val state: StateFlow<OnThisDayUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    /** Truy vấn lại theo ngày lịch hiện tại; lần gọi mới huỷ lần đang chạy. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(ioDispatcher) {
            _state.value = try {
                val target = calendar.currentTarget()
                val articles = articleDao.queryOnThisDay(
                    accountId = context.currentAccountId,
                    monthDay = target.monthDay,
                    currentYear = target.currentYear,
                    limit = RESULT_LIMIT,
                )
                if (articles.isEmpty()) {
                    OnThisDayUiState.Empty
                } else {
                    OnThisDayUiState.Content(articles, target.monthDay, target.currentYear.toInt())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "queryOnThisDay failed", e)
                OnThisDayUiState.Error(R.string.on_this_day_error)
            }
        }
    }

    private companion object {
        const val TAG = "OnThisDayViewModel"
        const val RESULT_LIMIT = 5
    }
}
