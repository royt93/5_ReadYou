package com.mckimquyen.reader.ui.page.home

import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.work.WorkManager
import com.mckimquyen.reader.domain.model.article.ArticleFlowItem
import com.mckimquyen.reader.domain.model.article.mapPagingFlowItem
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.general.Filter
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.domain.sv.SyncWorker
import com.mckimquyen.reader.infrastructure.android.AndroidStringsHelper
import com.mckimquyen.reader.infrastructure.di.ApplicationScope
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val rssService: RssSv,
    private val androidStringsHelper: AndroidStringsHelper,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
    private val workManager: WorkManager,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _filterUiState = MutableStateFlow(FilterState())
    val filterUiState = _filterUiState.asStateFlow()

    private val _showCommuteCast = MutableStateFlow(false)
    val showCommuteCast: StateFlow<Boolean> = _showCommuteCast.asStateFlow()

    fun openCommuteCast() {
        _showCommuteCast.value = true
    }

    fun closeCommuteCast() {
        _showCommuteCast.value = false
    }

    val syncWorkLiveData = workManager.getWorkInfosByTagLiveData(SyncWorker.WORK_NAME)

    fun sync() {
        viewModelScope.launch(ioDispatcher) {
            rssService.get().doSync()
        }
    }

    fun changeFilter(filterState: FilterState) {
        _filterUiState.update {
            it.copy(
                group = filterState.group,
                feed = filterState.feed,
                filter = filterState.filter,
            )
        }
        fetchArticles()
    }

    fun fetchArticles() {
        _homeUiState.update {
            it.copy(
                pagingData = Pager(
                    config = PagingConfig(
                        pageSize = 50,
                        enablePlaceholders = false,
                    )
                ) {
                    if (_homeUiState.value.searchContent.isNotBlank()) {
                        rssService.get().searchArticles(
                            content = _homeUiState.value.searchContent.trim(),
                            groupId = _filterUiState.value.group?.id,
                            feedId = _filterUiState.value.feed?.id,
                            isStarred = _filterUiState.value.filter.isStarred(),
                            isUnread = _filterUiState.value.filter.isUnread(),
                        )
                    } else {
                        rssService.get().pullArticles(
                            groupId = _filterUiState.value.group?.id,
                            feedId = _filterUiState.value.feed?.id,
                            isStarred = _filterUiState.value.filter.isStarred(),
                            isUnread = _filterUiState.value.filter.isUnread(),
                        )
                    }
                }.flow.map {
                    it.mapPagingFlowItem(androidStringsHelper)
                }.cachedIn(applicationScope)
            )
        }
    }

    fun inputSearchContent(content: String) {
        _homeUiState.update { it.copy(searchContent = content) }
        fetchArticles()
    }
}

@Keep
data class FilterState(
    val group: Group? = null,
    val feed: Feed? = null,
    val filter: Filter = Filter.All,
)

@Keep
data class HomeUiState(
    val pagingData: Flow<PagingData<ArticleFlowItem>> = emptyFlow(),
    val searchContent: String = "",
)
