package com.mckimquyen.reader.infrastructure.articlehistory

import android.content.Context
import android.util.Log
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.radar.RemovalTracker
import com.mckimquyen.reader.domain.radar.StoredArticleState
import com.mckimquyen.reader.domain.repository.ArticleRadarDao
import com.mckimquyen.reader.ui.ext.retractionRadarEnabled
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync-side entry point of EXC-10. Every method swallows its own failures (except cancellation) so that a radar
 * problem can never fail or abort a feed sync.
 */
@Singleton
open class RetractionRadarService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ArticleRadarDao,
    private val history: ArticleHistoryRepository,
) {

    open fun isEnabled(): Boolean = context.retractionRadarEnabled

    /**
     * Local RSS: [fetched] is the full current feed. Compares already-stored articles to their last snapshot and
     * advances removal counters. An empty list (failed or empty fetch) never counts as a miss.
     */
    open suspend fun processFetched(feed: Feed, fetched: List<Article>) = safely("processFetched") {
        if (!isEnabled()) return@safely
        val stored = fetched.mapNotNull { article ->
            dao.articleIdByLink(article.link, feed.id, feed.accountId)?.let { it to article }
        }
        stored.forEach { (id, article) -> history.record(id, article.title, article.body()) }

        val oldest = fetched.minOfOrNull { it.date.time }
        val tracked = if (oldest == null) emptyList() else dao.tracked(feed.id, feed.accountId, oldest)
        val updates = RemovalTracker.evaluate(
            fetchValid = fetched.isNotEmpty(),
            presentLinks = fetched.mapTo(HashSet()) { it.link },
            oldestFeedDate = oldest,
            stored = tracked.map { StoredArticleState(it.id, it.link, it.date, it.missedSyncs, it.removedAt != null) },
        )
        history.applyRemoval(updates)
    }

    /** Backends whose items have stable ids: compares those already stored; returns the ids it handled. */
    open suspend fun recordEdits(articles: List<Article>): Set<String> {
        var handled: Set<String> = emptySet()
        safely("recordEdits") {
            if (!isEnabled()) return@safely
            val existing = articles.map { it.id }.chunked(ID_CHUNK).flatMap { dao.existingIds(it) }.toSet()
            articles.filter { it.id in existing }.forEach { history.record(it.id, it.title, it.body()) }
            handled = existing
        }
        return handled
    }

    /** Stores the first snapshot of articles that have just been inserted (they must exist: FK). */
    open suspend fun recordBaselines(inserted: List<Article>) = safely("recordBaselines") {
        if (!isEnabled()) return@safely
        inserted.forEach { history.record(it.id, it.title, it.body()) }
    }

    open suspend fun purge() = safely("purge") { history.purge() }

    private fun Article.body(): String = fullContent ?: rawDescription

    private suspend fun safely(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Retraction radar $what failed", e)
        }
    }

    private companion object {
        const val TAG = "RLog"
        const val ID_CHUNK = 500
    }
}
