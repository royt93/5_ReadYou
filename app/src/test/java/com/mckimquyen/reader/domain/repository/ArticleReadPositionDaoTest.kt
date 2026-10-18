package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleReadPosition
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.db.MIGRATION_14_15
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ArticleReadPositionDaoTest {

    private lateinit var database: AndroidDatabase
    private lateinit var dao: ArticleReadPositionDao

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AndroidDatabase::class.java
        ).allowMainThreadQueries().addMigrations(MIGRATION_14_15).build()
        dao = database.articleReadPositionDao()

        // Insert prerequisite Feed and Article to satisfy foreign key constraint
        database.groupDao().insert(Group(id = "group-1", name = "Group", accountId = ACCOUNT_ID))
        database.feedDao().insert(Feed(id = "feed-1", name = "Feed", url = "https://example.com/feed", groupId = "group-1", accountId = ACCOUNT_ID))
        database.articleDao().insert(
            Article(
                id = ARTICLE_ID,
                date = Date(),
                title = "Test Article",
                rawDescription = "",
                shortDescription = "",
                link = "https://example.com/1",
                feedId = "feed-1",
                accountId = ACCOUNT_ID
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertPosition_replacesProgressAndGetPositionReturnsLatest() = kotlinx.coroutines.runBlocking {
        val first = position(progress = 0.25f)
        dao.upsertPosition(first)
        dao.upsertPosition(first.copy(progress = 0.75f, lastReadTime = NEWER_TIMESTAMP))

        val result = dao.getPosition(ARTICLE_ID)
        assertEquals(0.75f, result?.progress ?: 0f, 0.001f)
        assertEquals(NEWER_TIMESTAMP, result?.lastReadTime)
    }

    @Test
    fun deletePosition_removesStoredPosition() = kotlinx.coroutines.runBlocking {
        dao.upsertPosition(position(progress = 0.4f))

        dao.deletePosition(ARTICLE_ID)

        assertNull(dao.getPosition(ARTICLE_ID))
    }

    @Test
    fun markSyncedIfUnchanged_doesNotMarkNewerConcurrentPositionSynced() = kotlinx.coroutines.runBlocking {
        val snapshot = position(progress = 0.4f)
        dao.upsertPosition(snapshot)
        dao.upsertPosition(snapshot.copy(progress = 0.8f, lastReadTime = NEWER_TIMESTAMP, isSynced = false))

        val affectedRows = dao.markSyncedIfUnchanged(ARTICLE_ID, snapshot.lastReadTime, snapshot.progress)

        assertEquals(0, affectedRows)
        assertEquals(false, dao.getPosition(ARTICLE_ID)?.isSynced)
        assertEquals(0.8f, dao.getPosition(ARTICLE_ID)?.progress ?: 0f, 0.001f)
    }

    @Test
    fun deletingArticle_cascadesToReadPosition() = kotlinx.coroutines.runBlocking {
        dao.upsertPosition(position(progress = 0.4f))

        database.articleDao().deleteByFeedId(ACCOUNT_ID, "feed-1")

        assertNull(dao.getPosition(ARTICLE_ID))
    }

    private fun position(progress: Float) = ArticleReadPosition(
        articleId = ARTICLE_ID,
        accountId = ACCOUNT_ID,
        feedUrl = "https://example.com/feed",
        articleLink = "https://example.com/1",
        progress = progress,
        lastReadTime = OLDER_TIMESTAMP,
        isSynced = false,
    )

    private companion object {
        const val ARTICLE_ID = "article-1"
        const val ACCOUNT_ID = 1
        const val OLDER_TIMESTAMP = 1_700_000_000_000L
        const val NEWER_TIMESTAMP = 1_800_000_000_000L
    }
}
