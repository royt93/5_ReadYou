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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ArticleDaoQuizPeersTest {

    private lateinit var database: AndroidDatabase

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java
        ).allowMainThreadQueries().build()
        listOf(ACCOUNT to FEED, OTHER_ACCOUNT to OTHER_FEED).forEach { (account, feed) ->
            database.accountDao().insert(Account(id = account, name = "A$account", type = AccountType.Local))
            database.groupDao().insert(Group(id = "g$account", name = "G", accountId = account))
            database.feedDao().insert(Feed(id = feed, name = "F", url = "https://e.com/$feed", groupId = "g$account", accountId = account))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun queryQuizPeers_excludesSelfOtherAccountsAndBlankDescriptions_newestFirst() = runBlocking {
        insert("self", 5, "Self title", "Self description is here")
        insert("newer", 4, "Newer title", "Newer description is here")
        insert("older", 1, "Older title", "Older description is here")
        insert("blank", 3, "Blank title", "")
        insert("foreign", 6, "Foreign title", "Foreign description", account = OTHER_ACCOUNT, feed = OTHER_FEED)

        val peers = database.articleDao().queryQuizPeers("self", limit = 10)

        assertEquals(listOf("Newer title", "Older title"), peers.map { it.title })
        assertEquals("Newer description is here", peers.first().shortDescription)
    }

    @Test
    fun queryQuizPeers_respectsLimit() = runBlocking {
        insert("self", 100, "Self", "Self description is here")
        (1..10).forEach { insert("p$it", it.toLong(), "Title $it", "Description number $it here") }

        assertEquals(3, database.articleDao().queryQuizPeers("self", limit = 3).size)
    }

    @Test
    fun queryQuizPeers_unknownArticleYieldsNothing() = runBlocking {
        insert("a", 1, "Title", "Description is long enough")

        assertTrue(database.articleDao().queryQuizPeers("not-in-db", limit = 5).isEmpty())
    }

    @Test
    fun queryQuizPeers_doesNotReadTheBodyColumns() = runBlocking {
        insert("self", 2, "Self", "Self description is here")
        insert("big", 1, "Big", "Short desc is long enough", raw = "x".repeat(2_000_000))

        val peers = database.articleDao().queryQuizPeers("self", limit = 5)

        assertEquals(1, peers.size)
        assertEquals("Short desc is long enough", peers.single().shortDescription)
    }

    private suspend fun insert(
        id: String,
        day: Long,
        title: String,
        description: String,
        account: Int = ACCOUNT,
        feed: String = FEED,
        raw: String = "",
    ) {
        database.articleDao().insert(
            Article(
                id = id,
                date = Date(day * DAY_MS),
                title = title,
                rawDescription = raw,
                shortDescription = description,
                link = "https://example.com/$id",
                feedId = feed,
                accountId = account,
            )
        )
    }

    private companion object {
        const val ACCOUNT = 7
        const val OTHER_ACCOUNT = 8
        const val FEED = "feed-7"
        const val OTHER_FEED = "feed-8"
        const val DAY_MS = 86_400_000L
    }
}
