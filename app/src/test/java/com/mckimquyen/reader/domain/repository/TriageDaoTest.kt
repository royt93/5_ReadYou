package com.mckimquyen.reader.domain.repository

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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TriageDaoTest {

    private lateinit var db: AndroidDatabase
    private lateinit var articleDao: ArticleDao
    private lateinit var groupDao: GroupDao
    private lateinit var feedDao: FeedDao
    private lateinit var accountDao: AccountDao

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AndroidDatabase::class.java,
        ).allowMainThreadQueries().build()
        articleDao = db.articleDao()
        groupDao = db.groupDao()
        feedDao = db.feedDao()
        accountDao = db.accountDao()

        accountDao.insert(Account(id = 1, name = "Default", type = AccountType.Local))
        groupDao.insert(Group(id = "group1", name = "Group", accountId = 1))
        feedDao.insert(Feed(id = "feed1", name = "Tech News", url = "https://example.com/rss", groupId = "group1", accountId = 1))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sampleArticle(id: String, title: String, dateMillis: Long, isUnread: Boolean) = Article(
        id = id,
        date = Date(dateMillis),
        title = title,
        author = "Author",
        rawDescription = "Desc",
        shortDescription = "Short",
        link = "https://example.com/$id",
        feedId = "feed1",
        accountId = 1,
        isUnread = isUnread,
        isStarred = false,
    )

    @Test
    fun queryOldestUnreadArticleListWithFeed_returnsUnreadArticlesInAscendingDateOrder() = runBlocking {
        // Insert 3 articles with different dates: oldest (1000), middle (2000), newest (3000)
        articleDao.insert(sampleArticle(id = "a1", title = "Newest", dateMillis = 3000L, isUnread = true))
        articleDao.insert(sampleArticle(id = "a2", title = "Oldest", dateMillis = 1000L, isUnread = true))
        articleDao.insert(sampleArticle(id = "a3", title = "Middle", dateMillis = 2000L, isUnread = true))
        articleDao.insert(sampleArticle(id = "a4", title = "Read Article", dateMillis = 500L, isUnread = false))

        val count = articleDao.queryTotalUnreadCount(accountId = 1)
        assertEquals(3, count)

        val unreadList = articleDao.queryOldestUnreadArticleListWithFeed(accountId = 1, limit = 2)
        assertEquals(2, unreadList.size)
        // FIFO order: oldest first (a2, then a3)
        assertEquals("a2", unreadList[0].article.id)
        assertEquals("Oldest", unreadList[0].article.title)
        assertEquals("Tech News", unreadList[0].feed.name)

        assertEquals("a3", unreadList[1].article.id)
        assertEquals("Middle", unreadList[1].article.title)
    }
}
