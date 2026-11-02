package com.mckimquyen.reader.domain.galaxy

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GalaxyArticleDaoIntegrationTest {

    private lateinit var db: AndroidDatabase

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "Local", type = AccountType.Local))
        db.accountDao().insert(Account(id = 2, name = "Other", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g1", name = "Default", accountId = 1))
        db.feedDao().insert(Feed(id = "f1", name = "BBC News", url = "https://e.com/bbc", groupId = "g1", accountId = 1))
    }

    @After fun tearDown() = db.close()

    private suspend fun insert(id: String, date: Long, unread: Boolean, accountId: Int = 1, body: String = "") {
        db.articleDao().insert(
            Article(
                id = id, date = Date(date), title = "Title $id", author = null, rawDescription = body,
                shortDescription = "short $id", fullContent = body, img = null, link = "https://e.com/$id",
                feedId = "f1", accountId = accountId, isUnread = unread, isStarred = false, isReadLater = false,
            )
        )
    }

    @Test
    fun returnsOnlyReadArticlesOfTheAccountNewestFirstWithTheFeed() = runBlocking {
        insert("read_old", 1_000L, unread = false)
        insert("unread_new", 5_000L, unread = true)
        insert("read_new", 3_000L, unread = false)
        insert("other_account", 4_000L, unread = false, accountId = 2)

        val rows = db.articleDao().queryReadArticleListWithFeed(accountId = 1, limit = 10)

        assertEquals(listOf("read_new", "read_old"), rows.map { it.article.id })
        assertTrue(rows.all { !it.article.isUnread && it.feed.name == "BBC News" })
    }

    @Test
    fun ordersEqualDatesByIdDescendingAndHonoursTheLimit() = runBlocking {
        insert("a", 2_000L, unread = false)
        insert("b", 2_000L, unread = false)
        insert("c", 2_000L, unread = false)

        val rows = db.articleDao().queryReadArticleListWithFeed(accountId = 1, limit = 2)

        assertEquals(listOf("c", "b"), rows.map { it.article.id })
    }

    @Test
    fun readsMetadataOnlyEvenForArticlesWithAHugeBody() = runBlocking {
        insert("big", 1_000L, unread = false, body = "x".repeat(2 * 1024 * 1024))

        val row = db.articleDao().queryReadArticleListWithFeed(accountId = 1, limit = 1).single()

        // ArticleListItem has no body field at all: the query cannot have read the 2 MB columns (FIX-17).
        assertEquals("big", row.article.id)
        assertEquals("short big", row.article.shortDescription)
    }

    @Test
    fun returnsNothingWhenNoArticleWasRead() = runBlocking {
        insert("u1", 1_000L, unread = true)
        assertTrue(db.articleDao().queryReadArticleListWithFeed(accountId = 1, limit = 10).isEmpty())
    }
}
