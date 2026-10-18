package com.mckimquyen.reader.infrastructure.sync

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleReadPosition
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.ArticleReadPositionDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.infrastructure.backup.WebDavBackupManager
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.db.MIGRATION_14_15
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WebDavReadPositionSyncNetworkTest {

    private lateinit var server: MockWebServer
    private lateinit var database: AndroidDatabase
    private lateinit var positions: ArticleReadPositionDao
    private lateinit var articles: ArticleDao
    private lateinit var feeds: FeedDao
    private val backupManager = mockk<WebDavBackupManager>()

    @Before
    fun setUp() {
        runBlocking {
            server = MockWebServer()
            server.start()
            database = androidx.room.Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AndroidDatabase::class.java
            ).allowMainThreadQueries().addMigrations(MIGRATION_14_15).build()
            positions = database.articleReadPositionDao()
            articles = database.articleDao()
            feeds = database.feedDao()

            database.groupDao().insert(Group(id = GROUP_ID, name = "Sync", accountId = ACCOUNT_ID))
            feeds.insert(Feed(id = FEED_ID, name = "Example", url = FEED_URL, groupId = GROUP_ID, accountId = ACCOUNT_ID))
            articles.insert(article(ARTICLE_ID_A, ARTICLE_LINK_A))
            articles.insert(article(ARTICLE_ID_B, ARTICLE_LINK_B))
            every { backupManager.getConfig() } returns com.mckimquyen.reader.infrastructure.backup.WebDavConfig(
                serverUrl = server.url("/").toString(),
                username = USERNAME,
                password = PASSWORD,
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun syncPositions_uploadsMergedPortableRecordsAndMarksLocalSynced() = runBlocking {
        val remote = listOf(WebDavPositionRecord(FEED_URL, ARTICLE_LINK_A, REMOTE_PROGRESS, OLDER_TIMESTAMP))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(201))
        positions.upsertPosition(position(ARTICLE_ID_A, ARTICLE_LINK_A, LOCAL_PROGRESS))
        positions.upsertPosition(position(ARTICLE_ID_B, ARTICLE_LINK_B, SECOND_PROGRESS))
        val syncer = syncer()

        val result = syncer.syncPositions(ACCOUNT_ID)
        val getRequest = server.takeRequest()
        val putRequest = server.takeRequest()
        val uploaded = WebDavReadPositionSyncer.deserialize(putRequest.body.readUtf8())

        assertTrue(result.isSuccess)
        assertEquals("GET", getRequest.method)
        assertEquals("read_positions.json", getRequest.requestUrl?.encodedPath?.substringAfterLast('/'))
        assertEquals("PUT", putRequest.method)
        assertTrue(putRequest.getHeader("Authorization").orEmpty().startsWith("Basic "))
        assertEquals(2, uploaded.size)
        assertEquals(LOCAL_PROGRESS, uploaded.single { it.articleLink == ARTICLE_LINK_A }.progress)
        assertEquals(SECOND_PROGRESS, uploaded.single { it.articleLink == ARTICLE_LINK_B }.progress)
        assertTrue(positions.getPosition(ARTICLE_ID_A)?.isSynced == true)
        assertTrue(positions.getPosition(ARTICLE_ID_B)?.isSynced == true)
    }

    @Test
    fun syncPositions_preservesFurtherRemoteProgressWhenBothDevicesHavePosition() = runBlocking {
        val remote = listOf(WebDavPositionRecord(FEED_URL, ARTICLE_LINK_A, REMOTE_PROGRESS, OLDER_TIMESTAMP))
        server.enqueue(MockResponse().setBody(WebDavReadPositionSyncer.serialize(remote)))
        server.enqueue(MockResponse().setResponseCode(201))
        positions.upsertPosition(position(ARTICLE_ID_A, ARTICLE_LINK_A, LOCAL_PROGRESS))
        val syncer = syncer()

        val result = syncer.syncPositions(ACCOUNT_ID)
        server.takeRequest()
        val putRequest = server.takeRequest()
        val uploaded = WebDavReadPositionSyncer.deserialize(putRequest.body.readUtf8())

        assertTrue(result.isSuccess)
        assertEquals(REMOTE_PROGRESS, positions.getPosition(ARTICLE_ID_A)?.progress)
        assertEquals(REMOTE_PROGRESS, uploaded.single().progress)
        assertTrue(positions.getPosition(ARTICLE_ID_A)?.isSynced == true)
    }

    @Test
    fun syncPositions_uploadsLocalProgressWhenItIsFurtherThanRemote() = runBlocking {
        val remote = listOf(WebDavPositionRecord(FEED_URL, ARTICLE_LINK_A, LOCAL_PROGRESS, OLDER_TIMESTAMP))
        server.enqueue(MockResponse().setBody(WebDavReadPositionSyncer.serialize(remote)))
        server.enqueue(MockResponse().setResponseCode(201))
        positions.upsertPosition(position(ARTICLE_ID_A, ARTICLE_LINK_A, REMOTE_PROGRESS))

        val result = syncer().syncPositions(ACCOUNT_ID)
        server.takeRequest()
        val putRequest = server.takeRequest()
        val uploaded = WebDavReadPositionSyncer.deserialize(putRequest.body.readUtf8())

        assertTrue(result.isSuccess)
        assertEquals(REMOTE_PROGRESS, positions.getPosition(ARTICLE_ID_A)?.progress)
        assertEquals(REMOTE_PROGRESS, uploaded.single().progress)
        assertEquals(ACCOUNT_ID, positions.getPosition(ARTICLE_ID_A)?.accountId)
        assertTrue(positions.getPosition(ARTICLE_ID_A)?.isSynced == true)
    }

    @Test
    fun syncPositions_failsOnServerErrorAndLeavesLocalPositionUnsynced() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        positions.upsertPosition(position(ARTICLE_ID_A, ARTICLE_LINK_A, LOCAL_PROGRESS))

        val result = syncer().syncPositions(ACCOUNT_ID)

        assertTrue(result.isFailure)
        assertEquals(false, positions.getPosition(ARTICLE_ID_A)?.isSynced)
        assertEquals(1, server.requestCount)
    }

    private fun syncer() = WebDavReadPositionSyncer(
        webDavBackupManager = backupManager,
        readPositionDao = positions,
        feedDao = feeds,
        articleDao = articles,
        okHttpClient = OkHttpClient(),
        ioDispatcher = Dispatchers.IO,
    )

    private fun article(id: String, link: String) = Article(
        id = id,
        date = Date(),
        title = "Example",
        rawDescription = "",
        shortDescription = "",
        link = link,
        feedId = FEED_ID,
        accountId = ACCOUNT_ID,
    )

    private fun position(id: String, link: String, progress: Float) = ArticleReadPosition(
        articleId = id,
        accountId = ACCOUNT_ID,
        feedUrl = FEED_URL,
        articleLink = link,
        progress = progress,
        lastReadTime = OLDER_TIMESTAMP,
        isSynced = false,
    )

    private companion object {
        const val ACCOUNT_ID = 5
        const val GROUP_ID = "group-5"
        const val FEED_ID = "feed-5"
        const val ARTICLE_ID_A = "article-a"
        const val ARTICLE_ID_B = "article-b"
        const val FEED_URL = "https://example.com/feed.xml"
        const val ARTICLE_LINK_A = "https://example.com/a"
        const val ARTICLE_LINK_B = "https://example.com/b"
        const val USERNAME = "reader"
        const val PASSWORD = "webdav-secret"
        const val LOCAL_PROGRESS = 0.35f
        const val REMOTE_PROGRESS = 0.75f
        const val SECOND_PROGRESS = 0.5f
        const val OLDER_TIMESTAMP = 1_700_000_000_000L
    }
}
