package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.bounty.BountyQuerySanitizer
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BountyInvestigationRepositoryTest {

    private lateinit var db: AndroidDatabase
    private lateinit var repo: BountyInvestigationRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "A", type = AccountType.Local))
        db.accountDao().insert(Account(id = 2, name = "B", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g1", name = "G1", accountId = 1))
        db.groupDao().insert(Group(id = "g2", name = "G2", accountId = 2))
        db.feedDao().insert(Feed(id = "f1", name = "Feed One", url = "https://e.com/1", groupId = "g1", accountId = 1))
        db.feedDao().insert(Feed(id = "f2", name = "Feed Two", url = "https://e.com/2", groupId = "g1", accountId = 1))
        db.feedDao().insert(Feed(id = "f3", name = "Other Account", url = "https://e.com/3", groupId = "g2", accountId = 2))
        repo = BountyInvestigationRepository(db.articleDao())
    }

    @After
    fun tearDown() = db.close()

    private fun article(
        id: String,
        title: String,
        short: String = "",
        feedId: String = "f1",
        accountId: Int = 1,
        date: Long = 1_000L,
        body: String = "body",
    ) = Article(
        id = id, date = Date(date), title = title, rawDescription = body, shortDescription = short,
        fullContent = body, link = "https://e.com/$id", feedId = feedId, accountId = accountId,
    )

    private suspend fun insert(vararg articles: Article) = db.articleDao().insert(*articles)

    private suspend fun investigate(query: String, current: String = "cur", accountId: Int = 1) =
        repo.investigate(accountId, current, query)

    @Test
    fun matchesTitleAndShortDescription_caseInsensitively() = runBlocking {
        insert(
            article("cur", "Current article about DeepSeek"),
            article("t", "DeepSeek-R1 launches"),
            article("s", "Unrelated headline", short = "the deepseek model is discussed"),
            article("n", "Nothing relevant"),
        )

        val result = investigate("DeepSeek")

        assertEquals(setOf("t", "s"), result.articles.map { it.article.id }.toSet())
        assertEquals("DeepSeek", result.query)
    }

    @Test
    fun excludesCurrentArticleAndOtherAccounts() = runBlocking {
        insert(
            article("cur", "DeepSeek current"),
            article("same", "DeepSeek same account"),
            article("other", "DeepSeek other account", feedId = "f3", accountId = 2),
        )

        val ids = investigate("DeepSeek").articles.map { it.article.id }

        assertEquals(listOf("same"), ids)
    }

    @Test
    fun likeMetacharactersAreTreatedAsLiteralText() = runBlocking {
        insert(
            article("pct", "Growth hit 100% this year"),
            article("plain", "Growth hit 1000 this year"),
            article("under", "snake_case naming"),
            article("underWild", "snakeXcase naming"),
            article("slash", "path C:\\temp\\file"),
        )

        assertEquals(listOf("pct"), investigate("100%").articles.map { it.article.id })
        assertEquals(listOf("under"), investigate("snake_case").articles.map { it.article.id })
        assertEquals(listOf("slash"), investigate("C:\\temp").articles.map { it.article.id })
    }

    @Test
    fun resultsAreNewestFirstAndCappedAtLimit() = runBlocking {
        val many = (1..25).map { article("a$it", "Alpha story $it", date = it * 1_000L) }
        insert(article("cur", "Alpha current"), *many.toTypedArray())

        val articles = investigate("Alpha").articles

        assertEquals(BountyInvestigationRepository.MAX_RESULTS, articles.size)
        assertEquals("a25", articles.first().article.id)
        assertTrue(articles.zipWithNext().all { (a, b) -> a.article.date >= b.article.date })
    }

    @Test
    fun statsCountDistinctSourcesAndDateRange() = runBlocking {
        insert(
            article("a", "Topic one", feedId = "f1", date = 5_000L),
            article("b", "Topic two", feedId = "f2", date = 9_000L),
            article("c", "Topic three", feedId = "f1", date = 2_000L),
        )

        val result = investigate("Topic")

        assertEquals(3, result.articles.size)
        assertEquals(2, result.sourceCount)
        assertEquals(Date(2_000L), result.earliestDate)
        assertEquals(Date(9_000L), result.latestDate)
    }

    @Test
    fun noMatchesYieldsEmptyResultWithNullDates() = runBlocking {
        insert(article("a", "Something else entirely"))

        val result = investigate("Quantum")

        assertTrue(result.articles.isEmpty())
        assertEquals(0, result.sourceCount)
        assertNull(result.earliestDate)
        assertNull(result.latestDate)
    }

    @Test
    fun bodyTextIsNeverSearched() = runBlocking {
        insert(article("a", "Plain title", short = "plain summary", body = "secret Zebra keyword only in body"))

        assertTrue(investigate("Zebra").articles.isEmpty())
    }

    @Test
    fun sanitizedAndEscapedInputRoundTripsThroughRepository() = runBlocking {
        insert(article("a", "Intel gains 50% on news"))
        val query = requireNotNull(BountyQuerySanitizer.sanitize("\"50%\""))

        assertEquals(listOf("a"), repo.investigate(1, "cur", query).articles.map { it.article.id })
    }
}
