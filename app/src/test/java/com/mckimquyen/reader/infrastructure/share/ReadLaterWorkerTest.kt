package com.mckimquyen.reader.infrastructure.share

import android.content.Context
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleBody
import com.mckimquyen.reader.domain.model.article.FullContentPolicy
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.putBlocking
import com.mckimquyen.reader.domain.model.article.toListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadLaterWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val articleDao = mockk<ArticleDao>()
    private val rssHelper = mockk<RssHelper>()

    @Test
    fun rejectsOversizedParsedBodyWithoutUpdatingEntity() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountId, 1)
        val item = Article(id = "article-123", date = Date(), title = "Sample Story", rawDescription = "raw", shortDescription = "Sample", link = "https://example.com/story", feedId = Feed.READ_LATER_FEED_ID, accountId = 1).let { ArticleWithFeed(it, Feed(id = Feed.READ_LATER_FEED_ID, name = "Read Later", url = Feed.READ_LATER_URL, groupId = "group-1", accountId = 1)) }
        coEvery { workerParams.inputData } returns workDataOf(ReadLaterWorker.KEY_ARTICLE_ID to "article-123", ReadLaterWorker.KEY_URL to "https://example.com/story", ReadLaterWorker.KEY_TITLE to "Sample Story")
        coEvery { articleDao.queryArticleListWithFeedById(1, "article-123") } returns item.toListItemWithFeed()
        coEvery { articleDao.queryArticleBody(1, "article-123") } returns ArticleBody("article-123", "raw", null)
        coEvery { rssHelper.parseFullContent(any(), any(), any()) } returns "x".repeat(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES + 1)
        val result = ReadLaterWorker(context, workerParams, articleDao, rssHelper).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { articleDao.updateFullContent(any(), any(), any()) }
        coVerify(exactly = 0) { articleDao.update(any()) }
    }

    @Test
    fun skipsNetworkWhenArticleAlreadyHasFullContent() = runBlocking {
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountId, 1)
        val item = Article(id = "article-123", date = Date(), title = "Sample Story", rawDescription = "raw", shortDescription = "Sample", link = "https://example.com/story", feedId = Feed.READ_LATER_FEED_ID, accountId = 1).let { ArticleWithFeed(it, Feed(id = Feed.READ_LATER_FEED_ID, name = "Read Later", url = Feed.READ_LATER_URL, groupId = "group-1", accountId = 1)) }
        coEvery { workerParams.inputData } returns workDataOf(ReadLaterWorker.KEY_ARTICLE_ID to "article-123", ReadLaterWorker.KEY_URL to "https://example.com/story", ReadLaterWorker.KEY_TITLE to "Sample Story")
        coEvery { articleDao.queryArticleListWithFeedById(1, "article-123") } returns item.toListItemWithFeed()
        coEvery { articleDao.queryArticleBody(1, "article-123") } returns ArticleBody("article-123", "raw", "cached")
        assertEquals(ListenableWorker.Result.success(), ReadLaterWorker(context, workerParams, articleDao, rssHelper).doWork())
        coVerify(exactly = 0) { rssHelper.parseFullContent(any(), any(), any()) }
        coVerify(exactly = 0) { articleDao.updateFullContent(any(), any(), any()) }
    }

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
        context.dataStore.putBlocking(DataStoreKeys.CurrentAccountId, 1)
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
        coEvery { articleDao.queryArticleListWithFeedById(1, "article-123") } returns articleWithFeed.toListItemWithFeed()
        coEvery { articleDao.queryArticleBody(1, "article-123") } returns ArticleBody("article-123", "", null)
        coEvery { rssHelper.parseFullContent("https://example.com/story", "Sample Story", any()) } returns "<p>Clean readable text</p>"
        coEvery { articleDao.updateFullContent(1, "article-123", any()) } returns Unit

        val worker = ReadLaterWorker(context, workerParams, articleDao, rssHelper)
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        coVerify(exactly = 1) {
            articleDao.updateFullContent(1, "article-123", "<p>Clean readable text</p>")
        }
        coVerify(exactly = 0) { articleDao.update(any()) }
    }
}
