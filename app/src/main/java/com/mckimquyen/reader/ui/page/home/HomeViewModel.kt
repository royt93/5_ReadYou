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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import com.mckimquyen.reader.domain.model.cluster.StoryCluster
import com.mckimquyen.reader.domain.model.cluster.StoryClusterResult
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.clustering.StoryClusteringEngine
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchResult
import com.mckimquyen.reader.infrastructure.watchdog.WatchdogManager
import com.mckimquyen.reader.domain.model.watchdog.WatchdogKeyword
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.flowSemanticSearch
import com.mckimquyen.reader.ui.ext.flowStoryClustering
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val articleDao: ArticleDao,
    private val clusteringEngine: StoryClusteringEngine,
    private val semanticSearchEngine: SemanticSearchEngine,
    private val rssService: RssSv,
    private val watchdogManager: WatchdogManager,
    private val androidStringsHelper: AndroidStringsHelper,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
    private val workManager: WorkManager,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _semanticSearchResults = MutableStateFlow<List<SemanticSearchResult>>(emptyList())
    val semanticSearchResults: StateFlow<List<SemanticSearchResult>> = _semanticSearchResults.asStateFlow()

    private val _clusterResult = MutableStateFlow(StoryClusterResult.EMPTY)
    val clusterResult: StateFlow<StoryClusterResult> = _clusterResult.asStateFlow()

    private val _selectedCluster = MutableStateFlow<StoryCluster?>(null)
    val selectedCluster: StateFlow<StoryCluster?> = _selectedCluster.asStateFlow()

    fun openCluster(cluster: StoryCluster) {
        _selectedCluster.value = cluster
    }

    fun closeCluster() {
        _selectedCluster.value = null
    }

    fun markClusterAsRead(cluster: StoryCluster) {
        viewModelScope.launch(ioDispatcher) {
            val accountId = context.currentAccountId
            cluster.articles.forEach {
                articleDao.markAsReadByArticleId(accountId, it.article.id, isUnread = false)
            }
            closeCluster()
            fetchArticles()
        }
    }

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

    val watchdogKeywords: StateFlow<List<WatchdogKeyword>> = watchdogManager.keywords

    private val _showWatchdogSheet = MutableStateFlow(false)
    val showWatchdogSheet: StateFlow<Boolean> = _showWatchdogSheet.asStateFlow()

    fun openWatchdogSheet() {
        _showWatchdogSheet.value = true
    }

    fun closeWatchdogSheet() {
        _showWatchdogSheet.value = false
    }

    fun addWatchdogKeyword(keyword: String): Boolean = watchdogManager.addKeyword(keyword)

    fun removeWatchdogKeyword(id: String) = watchdogManager.removeKeyword(id)

    fun toggleWatchdogKeyword(id: String, isEnabled: Boolean) = watchdogManager.toggleKeyword(id, isEnabled)

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

    private var fetchArticlesJob: Job? = null

    fun fetchArticles(debounceMs: Long = 0L) {
        // Cancel any in-flight fetch so a slower, now-stale query can never overwrite
        // the result of a query started more recently (e.g. fast typing in search).
        fetchArticlesJob?.cancel()
        fetchArticlesJob = viewModelScope.launch(ioDispatcher) {
            if (debounceMs > 0) delay(debounceMs)
            val searchContent = _homeUiState.value.searchContent.trim()
            val isClusteringEnabled = context.flowStoryClustering
            val clusterResult = if (isClusteringEnabled && searchContent.isBlank()) {
                val accountId = context.currentAccountId
                val recentArticles = articleDao.queryRecentArticlesWithFeed(accountId, limit = 150)
                clusteringEngine.cluster(recentArticles)
            } else {
                StoryClusterResult.EMPTY
            }
            _clusterResult.value = clusterResult

            if (searchContent.isNotBlank() && context.flowSemanticSearch) {
                val accountId = context.currentAccountId
                val candidates = articleDao.queryRecentArticlesWithFeed(accountId, limit = 200)
                _semanticSearchResults.value = semanticSearchEngine.rank(searchContent, candidates)
            } else {
                _semanticSearchResults.value = emptyList()
            }

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
                    }.flow.map { pagingData ->
                        pagingData.mapPagingFlowItem(androidStringsHelper, clusterResult)
                    }.cachedIn(applicationScope)
                )
            }
        }
    }

    fun inputSearchContent(content: String) {
        _homeUiState.update { it.copy(searchContent = content) }
        fetchArticles(debounceMs = SEARCH_DEBOUNCE_MS)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
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
