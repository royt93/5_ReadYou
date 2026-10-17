package com.mckimquyen.reader.infrastructure.share

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class ReadLaterWorkerTest {
    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val articleDao = mockk<ArticleDao>()
    private val rssHelper = mockk<RssHelper>()

    @Test
    fun returnsFailureWhenArticleIdOrUrlMissing() = runBlocking {
        coEvery { workerParams.inputData } returns workDataOf()
        val worker = ReadLaterWorker(context, workerParams, articleDao, rssHelper)
        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    @Test
    fun updatesArticleWithFullContentOnSuccess() = runBlocking {
        val feed = Feed(
            id = Feed.READ_LATER_FEED_ID,
            name = "Read Later",
            url = Feed.READ_LATER_URL,
            groupId = "group-1",
            accountId = 1
        )
        val article = Article(
            id = "article-123",
            date = Date(),
            title = "Sample Story",
            rawDescription = "",
            shortDescription = "Sample",
            link = "https://example.com/story",
            feedId = Feed.READ_LATER_FEED_ID,
            accountId = 1,
            isReadLater = true
        )
        val articleWithFeed = ArticleWithFeed(article = article, feed = feed)

        coEvery { workerParams.inputData } returns workDataOf(
            ReadLaterWorker.KEY_ARTICLE_ID to "article-123",
            ReadLaterWorker.KEY_URL to "https://example.com/story",
            ReadLaterWorker.KEY_TITLE to "Sample Story"
        )
        coEvery { articleDao.queryById("article-123") } returns articleWithFeed
        coEvery { rssHelper.parseFullContent("https://example.com/story", "Sample Story", any()) } returns "<p>Clean readable text</p>"
        coEvery { articleDao.update(any()) } returns Unit

        val worker = ReadLaterWorker(context, workerParams, articleDao, rssHelper)
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        coVerify(exactly = 1) {
            articleDao.update(match { it.id == "article-123" && it.fullContent == "<p>Clean readable text</p>" })
        }
    }
}
