package com.mckimquyen.reader.ui.page.reels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.infrastructure.media.video.ArticleVideoExtractor
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject

/**
 * Feeds the full-screen reels pager.
 *
 * Summaries come from [ArticleHighlightsExtractor.extractOfflineHighlights], which is pure offline
 * heuristics — no network call, no API key — so cards are ready instantly and work in airplane mode.
 */
@HiltViewModel
class ReelsViewModel @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val articleDao: ArticleDao,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReelsUiState())
    val uiState: StateFlow<ReelsUiState> = _uiState.asStateFlow()

    init {
        loadReels()
    }

    fun loadReels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val articles = withContext(ioDispatcher) {
                articleDao.queryRecentArticlesWithFeed(context.currentAccountId, limit = REEL_LIMIT)
            }
            // Summarising is CPU work over article bodies, so keep it off the IO pool and off Main.
            val items = withContext(defaultDispatcher) { articles.map(::buildItem) }

            _uiState.value = ReelsUiState(items = items, isLoading = false)
        }
    }

    private fun buildItem(articleWithFeed: ArticleWithFeed): ReelItem {
        val article = articleWithFeed.article
        val html = article.fullContent ?: article.rawDescription

        return articleWithFeed.toReelItem(
            bullets = summarize(title = article.title, html = html),
            hasVideo = ArticleVideoExtractor.extract(html, article.link).isNotEmpty(),
        )
    }

    private fun summarize(title: String, html: String?): List<String> {
        val plainText = plainTextOf(html)
        if (plainText.isBlank()) return emptyList()

        val highlights = ArticleHighlightsExtractor.extractOfflineHighlights(title, plainText)
        val bullets = highlights.keyTakeaways.take(BULLET_COUNT)
        // Very short articles can yield no takeaways; the TL;DR is still better than a blank card.
        return bullets.ifEmpty { listOfNotNull(highlights.tldr.ifBlank { null }) }
    }

    private fun plainTextOf(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return runCatching { Jsoup.parse(html).text() }.getOrDefault("").trim()
    }

    companion object {
        /** Bullets per card, per the reels design. */
        const val BULLET_COUNT = 3

        /** Cards built per open; matches the recent-article window used elsewhere in the app. */
        const val REEL_LIMIT = 50
    }
}
