package com.mckimquyen.reader.ui.page.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.sv.SystemReadLaterFeedManager
import com.mckimquyen.reader.infrastructure.share.ReadLaterWorker
import com.mckimquyen.reader.infrastructure.share.UrlExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class ShareTargetUiState(
    val targetUrl: String? = null,
    val title: String = "",
    val isLoading: Boolean = false,
    val savedArticleId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ShareTargetViewModel @Inject constructor(
    private val feedManager: SystemReadLaterFeedManager,
    private val articleDao: ArticleDao,
    private val workManager: WorkManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShareTargetUiState())
    val uiState: StateFlow<ShareTargetUiState> = _uiState.asStateFlow()

    fun initFromShareText(text: String?) {
        val url = UrlExtractor.extractValidUrl(text)
        if (url == null) {
            _uiState.update { it.copy(errorMessage = "invalid_url") }
        } else {
            _uiState.update { it.copy(targetUrl = url, title = url) }
        }
    }

    fun saveArticle(accountId: Int, defaultTitle: String, onSaved: () -> Unit) {
        val url = _uiState.value.targetUrl ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val feed = feedManager.ensureReadLaterFeed(accountId, defaultTitle)
            val articleId = UUID.randomUUID().toString()
            val article = Article(
                id = articleId,
                date = Date(),
                title = _uiState.value.title.ifBlank { url },
                rawDescription = "",
                shortDescription = url,
                link = url,
                feedId = feed.id,
                accountId = accountId,
                isUnread = true,
                isReadLater = true
            )
            articleDao.insert(article)

            val workRequest = OneTimeWorkRequestBuilder<ReadLaterWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(
                    workDataOf(
                        ReadLaterWorker.KEY_ARTICLE_ID to articleId,
                        ReadLaterWorker.KEY_URL to url,
                        ReadLaterWorker.KEY_TITLE to article.title
                    )
                )
                .build()
            workManager.enqueue(workRequest)

            _uiState.update { it.copy(isLoading = false, savedArticleId = articleId) }
            onSaved()
        }
    }
}
