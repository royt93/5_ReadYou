package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.db.MIGRATION_14_15
import com.mckimquyen.reader.infrastructure.db.MIGRATION_15_16
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ArticleDaoReadCollectionsTest {

    private lateinit var database: AndroidDatabase

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java
        ).allowMainThreadQueries().addMigrations(MIGRATION_14_15, MIGRATION_15_16).build()
        database.accountDao().insert(Account(id = ACCOUNT_ID, name = "Test", type = AccountType.Local))
        database.groupDao().insert(Group(id = GROUP_ID, name = "Group", accountId = ACCOUNT_ID))
        database.feedDao().insert(
            Feed(id = FEED_ID, name = "Feed", url = FEED_URL, groupId = GROUP_ID, accountId = ACCOUNT_ID)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun queryAllArticlesWithFeed_returnsAllArticlesForRequestedAccount() = runBlocking {
        database.accountDao().insert(Account(id = OTHER_ACCOUNT_ID, name = "Other", type = AccountType.Local))
        database.groupDao().insert(Group(id = OTHER_GROUP_ID, name = "Other group", accountId = OTHER_ACCOUNT_ID))
        database.feedDao().insert(
            Feed(id = OTHER_FEED_ID, name = "Other feed", url = OTHER_FEED_URL, groupId = OTHER_GROUP_ID, accountId = OTHER_ACCOUNT_ID)
        )
        insertArticle("article-1", ACCOUNT_ID, FEED_ID)
        insertArticle("article-2", ACCOUNT_ID, FEED_ID)
        insertArticle("other-article", OTHER_ACCOUNT_ID, OTHER_FEED_ID)

        val results = database.articleDao().queryArticlesWithFeedPage(ACCOUNT_ID, limit = PAGE_SIZE, offset = 0)

        assertEquals(setOf("article-1", "article-2"), results.map { it.article.id }.toSet())
        assertEquals(setOf(FEED_ID), results.map { it.feed.id }.toSet())
    }

    private suspend fun insertArticle(id: String, accountId: Int, feedId: String) {
        database.articleDao().insert(
            Article(
                id = id,
                date = Date(),
                title = id,
                rawDescription = "",
                shortDescription = "",
                link = "https://example.com/$id",
                feedId = feedId,
                accountId = accountId,
            )
        )
    }

    private companion object {
        const val ACCOUNT_ID = 7
        const val OTHER_ACCOUNT_ID = 8
        const val GROUP_ID = "group-7"
        const val OTHER_GROUP_ID = "group-8"
        const val FEED_ID = "feed-7"
        const val OTHER_FEED_ID = "feed-8"
        const val FEED_URL = "https://example.com/feed-7"
        const val OTHER_FEED_URL = "https://example.com/feed-8"
        const val PAGE_SIZE = 10
    }
}
