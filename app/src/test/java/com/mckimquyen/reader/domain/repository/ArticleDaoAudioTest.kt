package com.mckimquyen.reader.domain.repository

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.db.MIGRATION_14_15
import com.mckimquyen.reader.infrastructure.db.MIGRATION_15_16
import com.mckimquyen.reader.infrastructure.db.MIGRATION_16_17
import com.mckimquyen.reader.infrastructure.db.MIGRATION_17_18
import kotlinx.coroutines.runBlocking
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
class ArticleDaoAudioTest {

    private lateinit var database: AndroidDatabase
    private val dao get() = database.articleDao()

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java
        ).allowMainThreadQueries()
            .addMigrations(MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18).build()
        database.accountDao().insert(Account(id = ACCOUNT_ID, name = "Test", type = AccountType.Local))
        database.groupDao().insert(Group(id = GROUP_ID, name = "Group", accountId = ACCOUNT_ID))
        database.feedDao().insert(
            Feed(id = FEED_ID, name = "Feed", url = "https://example.com/feed", groupId = GROUP_ID, accountId = ACCOUNT_ID)
        )
        dao.insert(
            Article(
                id = ARTICLE_ID, date = Date(), title = "Episode", rawDescription = "", shortDescription = "",
                link = "https://example.com/e", feedId = FEED_ID, accountId = ACCOUNT_ID, isUnread = true,
                audioUrl = AUDIO_URL, audioMime = AUDIO_MIME, audioDurationSec = AUDIO_DURATION,
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun assertAudio(article: Article?) {
        assertEquals(AUDIO_URL, article?.audioUrl)
        assertEquals(AUDIO_MIME, article?.audioMime)
        assertEquals(AUDIO_DURATION, article?.audioDurationSec)
    }

    private suspend fun firstOf(source: PagingSource<Int, ArticleWithFeed>): Article? {
        val result = source.load(PagingSource.LoadParams.Refresh(null, PAGE_SIZE, false))
        return (result as PagingSource.LoadResult.Page).data.firstOrNull()?.article
    }

    @Test
    fun queryById_roundTripsAudioFields() = runBlocking {
        assertAudio(dao.queryById(ARTICLE_ID)?.article)
    }

    @Test
    fun unreadList_returnsAudioFields() = runBlocking {
        assertAudio(firstOf(dao.queryArticleWithFeedWhenIsUnread(ACCOUNT_ID, true)))
    }

    @Test
    fun groupAllList_returnsAudioFields() = runBlocking {
        assertAudio(firstOf(dao.queryArticleWithFeedByGroupIdWhenIsAll(ACCOUNT_ID, GROUP_ID)))
    }

    @Test
    fun groupUnreadList_returnsAudioFields() = runBlocking {
        assertAudio(firstOf(dao.queryArticleWithFeedByGroupIdWhenIsUnread(ACCOUNT_ID, GROUP_ID, true)))
    }

    @Test
    fun latestByFeed_returnsAudioFields() = runBlocking {
        assertAudio(dao.queryLatestByFeedId(ACCOUNT_ID, FEED_ID))
    }

    @Test
    fun articleWithoutAudio_hasNullFields() = runBlocking {
        dao.insert(
            Article(
                id = "plain", date = Date(), title = "Plain", rawDescription = "", shortDescription = "",
                link = "https://example.com/p", feedId = FEED_ID, accountId = ACCOUNT_ID,
            )
        )
        val plain = dao.queryById("plain")?.article
        assertNull(plain?.audioUrl)
        assertNull(plain?.audioMime)
        assertNull(plain?.audioDurationSec)
    }

    private companion object {
        const val ACCOUNT_ID = 1
        const val GROUP_ID = "g1"
        const val FEED_ID = "f1"
        const val ARTICLE_ID = "a1"
        const val AUDIO_URL = "https://cdn.example.com/ep1.mp3"
        const val AUDIO_MIME = "audio/mpeg"
        const val AUDIO_DURATION = 1913
        const val PAGE_SIZE = 20
    }
}
