package com.mckimquyen.reader.infrastructure.share

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ReadLaterWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val articleDao: ArticleDao,
    private val rssHelper: RssHelper,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val articleId = inputData.getString(KEY_ARTICLE_ID) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val fallbackTitle = inputData.getString(KEY_TITLE).orEmpty()

        val articleWithFeed = articleDao.queryById(articleId) ?: return@withContext Result.failure()
        val article = articleWithFeed.article
        val cleanContent = runCatching {
            rssHelper.parseFullContent(url, fallbackTitle.ifBlank { article.title }, sourceType = Feed.WEB_SOURCE_TYPE)
        }.getOrDefault("")

        if (cleanContent.isNotBlank()) {
            article.fullContent = cleanContent
            articleDao.update(article)
        }
        Result.success()
    }

    companion object {
        const val KEY_ARTICLE_ID = "key_article_id"
        const val KEY_URL = "key_url"
        const val KEY_TITLE = "key_title"
    }
}
