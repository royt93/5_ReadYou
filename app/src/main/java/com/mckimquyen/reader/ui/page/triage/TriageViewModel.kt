package com.mckimquyen.reader.ui.page.triage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.repository.WidgetRefreshNotifier
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TriageViewModel @Inject constructor(
    private val articleDao: ArticleDao,
    private val widgetRefreshNotifier: WidgetRefreshNotifier,
    private val brainRpgRepository: BrainRpgRepository,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    internal constructor(
        accountId: Int,
        articleDao: ArticleDao,
        widgetRefreshNotifier: WidgetRefreshNotifier,
        brainRpgRepository: BrainRpgRepository,
        ioDispatcher: CoroutineDispatcher,
    ) : this(articleDao, widgetRefreshNotifier, brainRpgRepository, ioDispatcher) {
        this.currentAccountId = accountId
        loadInitialData()
    }

    private var currentAccountId: Int = 1

    private val _uiState = MutableStateFlow(TriageUiState())
    val uiState: StateFlow<TriageUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    fun setAccountId(accountId: Int) {
        if (currentAccountId != accountId) {
            currentAccountId = accountId
            loadInitialData()
        }
    }

    fun loadInitialData() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoading = true) }
            val total = articleDao.queryTotalUnreadCount(currentAccountId)
            if (total == 0) {
                _uiState.update { it.copy(isLoading = false, isInboxZero = true, totalCount = 0) }
                return@launch
            }
            val list = articleDao.queryOldestUnreadArticleListWithFeed(currentAccountId, limit = 50)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    articles = list,
                    totalCount = total,
                    isInboxZero = list.isEmpty(),
                )
            }
        }
    }

    fun skipCurrent() {
        val current = _uiState.value.currentArticle ?: return
        viewModelScope.launch(ioDispatcher) {
            articleDao.markAsReadByArticleId(currentAccountId, current.article.id, false)
            widgetRefreshNotifier.refresh()
            val xpDelta = brainRpgRepository.awardTriageXpOnce(current.article.id, 5L)
            advanceCard(isSaved = false, xp = xpDelta)
        }
    }

    fun saveCurrent() {
        val current = _uiState.value.currentArticle ?: return
        viewModelScope.launch(ioDispatcher) {
            articleDao.markAsStarredByArticleId(currentAccountId, current.article.id, true)
            articleDao.markAsReadByArticleId(currentAccountId, current.article.id, false)
            widgetRefreshNotifier.refresh()
            val xpDelta = brainRpgRepository.awardTriageXpOnce(current.article.id, 5L)
            advanceCard(isSaved = true, xp = xpDelta)
        }
    }

    fun markCurrentReadForNavigation(): String? {
        val current = _uiState.value.currentArticle ?: return null
        val articleId = current.article.id
        viewModelScope.launch(ioDispatcher) {
            articleDao.markAsReadByArticleId(currentAccountId, articleId, false)
            widgetRefreshNotifier.refresh()
            val xpDelta = brainRpgRepository.awardTriageXpOnce(articleId, 5L)
            advanceCard(isSaved = false, xp = xpDelta)
        }
        return articleId
    }

    private suspend fun advanceCard(isSaved: Boolean, xp: Long) {
        _uiState.update { state ->
            val remaining = state.articles.drop(1)
            val newProcessed = state.processedCount + 1
            val newSaved = state.savedCount + if (isSaved) 1 else 0
            val newSkipped = state.skippedCount + if (!isSaved) 1 else 0
            val newXp = state.totalXpEarned + xp
            val isZero = remaining.isEmpty() && newProcessed >= state.totalCount
            state.copy(
                articles = remaining,
                processedCount = newProcessed,
                savedCount = newSaved,
                skippedCount = newSkipped,
                totalXpEarned = newXp,
                isInboxZero = isZero,
            )
        }

        val state = _uiState.value
        if (state.articles.size < 5 && state.processedCount < state.totalCount) {
            val more = articleDao.queryOldestUnreadArticleListWithFeed(currentAccountId, limit = 50)
            if (more.isNotEmpty()) {
                _uiState.update { current ->
                    val combined = (current.articles + more).distinctBy { it.article.id }
                    current.copy(articles = combined)
                }
            } else if (_uiState.value.articles.isEmpty()) {
                _uiState.update { it.copy(isInboxZero = true) }
            }
        }
    }
}
