package com.mckimquyen.reader.infrastructure.ai

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.oracle.OracleBet
import com.mckimquyen.reader.domain.model.oracle.OracleBetSide
import com.mckimquyen.reader.domain.model.oracle.OracleBetStatus
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleEvidenceFinderTest {

    private lateinit var db: AndroidDatabase
    private lateinit var finder: OracleEvidenceFinder
    private val day = 86_400_000L
    private val placedAt = 100 * day
    private val question = "Will the ceasefire hold until the end of next month?"

    @Before
    fun setUp() = runBlocking {
        ShadowLog.clear()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "A", type = AccountType.Local))
        db.accountDao().insert(Account(id = 2, name = "B", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g", name = "G", accountId = 1))
        db.groupDao().insert(Group(id = "g2", name = "G2", accountId = 2))
        db.feedDao().insert(Feed(id = "f", name = "Wire News", url = "https://e.com/f", groupId = "g", accountId = 1))
        db.feedDao().insert(Feed(id = "f2", name = "Other", url = "https://e.com/f2", groupId = "g2", accountId = 2))
        finder = OracleEvidenceFinder(db.articleDao(), SemanticSearchEngine(db.articleEmbeddingDao()))
    }

    @After
    fun tearDown() = db.close()

    private fun article(id: String, title: String, date: Long, desc: String = "", account: Int = 1) = Article(
        id = id, date = Date(date), title = title, rawDescription = "RAW-SECRET-$id", shortDescription = desc,
        link = "https://secret.example/$id", feedId = if (account == 1) "f" else "f2", accountId = account,
    )

    private fun put(vararg a: Article) = runBlocking { db.articleDao().insertOnConflictIgnore(*a) }

    private fun bet(placed: Long = placedAt) = OracleBet(
        id = 1, articleId = "bet-article", question = question, expiresAt = placed + day, side = OracleBetSide.YES,
        stake = 100, status = OracleBetStatus.OPEN, placedAt = placed,
    )

    private fun find(b: OracleBet = bet()) = runBlocking { finder.find(1, b) }

    @Test
    fun relatedArticleAfterTheBet_isFound_withItsFeedNameAndDate() {
        put(article("a", "Ceasefire holds as talks continue next month", placedAt + 2 * day, "The ceasefire hold continues"))
        val out = find()
        assertEquals(listOf("a"), out.map { it.articleId })
        assertEquals("Wire News", out.single().feedName)
        assertEquals(placedAt + 2 * day, out.single().dateMillis)
        assertEquals("Ceasefire holds as talks continue next month", out.single().title)
    }

    @Test
    fun anArticleOlderThanTheBet_isNeverEvidence() {
        put(article("old", "Ceasefire hold expected until the end of next month", placedAt - day, "ceasefire hold"))
        assertTrue(find().isEmpty())
    }

    @Test
    fun anUnrelatedArticle_isNotEvidence() {
        put(article("x", "Recipe for a vegetarian lasagna with spinach", placedAt + day, "Layer the pasta and cheese"))
        assertTrue(find().isEmpty())
    }

    @Test
    fun atMostThree_evenWithManyRelatedArticles() {
        put(*(1..8).map { article("r$it", "Ceasefire hold update number $it next month", placedAt + it * day, "ceasefire hold") }.toTypedArray())
        assertEquals(3, find().size)
    }

    @Test
    fun onlyTheBetsAccount_isSearched() {
        put(article("mine", "Ceasefire hold update next month", placedAt + day, "ceasefire hold", account = 1))
        put(article("theirs", "Ceasefire hold update next month", placedAt + day, "ceasefire hold", account = 2))
        assertEquals(listOf("mine"), find().map { it.articleId })
    }

    @Test
    fun theAccountComesFromTheCaller_notAFixedOne() {
        put(article("mine", "Ceasefire hold update next month", placedAt + day, "ceasefire hold", account = 1))
        put(article("theirs", "Ceasefire hold update next month", placedAt + day, "ceasefire hold", account = 2))
        assertEquals(listOf("theirs"), runBlocking { finder.find(2, bet()) }.map { it.articleId })
        assertEquals(listOf("mine"), runBlocking { finder.find(1, bet()) }.map { it.articleId })
    }

    @Test
    fun nothingSaved_isEmpty() = assertTrue(find().isEmpty())

    @Test
    fun theScanIsLimitedToTheTwoHundredNewestArticles() {
        // The fillers are on the same topic as the question (all clearly above the relevance bar) but only 3 can be shown.
        // The single OLDEST article is the 201st newest and is the only one with a unique title, so if the scan were not
        // limited it would still lose to the 3 best; the point is checked by the next test, using a count instead.
        put(*(1..200).map { n ->
            article("filler$n", "Ceasefire hold update number $n next month", placedAt + 10 * day + n, "ceasefire hold")
        }.toTypedArray())
        put(article("buried", "Ceasefire hold update next month the ceasefire hold", placedAt + day, "ceasefire hold"))
        val ids = find().map { it.articleId }
        assertEquals(3, ids.size)
        assertTrue("the article outside the 200 newest must not be scanned", "buried" !in ids)
    }

    @Test
    fun anOlderRelatedArticle_isScanned_whenItIsAmongTheTwoHundredNewest() {
        put(*(1..199).map { n ->
            article("filler$n", "Quarterly earnings beat expectations as chipmaker revenue grows part $n", placedAt + 10 * day + n, "earnings revenue")
        }.toTypedArray())
        put(article("edge", "Ceasefire hold update next month the ceasefire hold", placedAt + day, "ceasefire hold"))
        assertEquals(listOf("edge"), find().map { it.articleId })
    }

    @Test
    fun aFailureInTheSearch_returnsEmpty_andLogsOnlyTheExceptionType() {
        val dao = mockk<ArticleDao>()
        coEvery { dao.queryRecentArticleListWithFeed(any(), any()) } throws IllegalStateException("SecretQuestionText ceasefire")
        val failing = OracleEvidenceFinder(dao, SemanticSearchEngine(db.articleEmbeddingDao()))
        assertTrue(runBlocking { failing.find(1, bet()) }.isEmpty())
        val log = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable?.message.orEmpty()}" }
        assertTrue(log.contains("IllegalStateException"))
        assertFalse("the log must not carry the question", log.contains("SecretQuestionText"))
    }

    @Test
    fun cancellation_isRethrown() {
        val dao = mockk<ArticleDao>()
        coEvery { dao.queryRecentArticleListWithFeed(any(), any()) } throws CancellationException("cancelled")
        val cancelling = OracleEvidenceFinder(dao, SemanticSearchEngine(db.articleEmbeddingDao()))
        val thrown = runCatching { runBlocking { cancelling.find(1, bet()) } }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    // --- source: no network, no Gemini, no article bodies

    private val source = File("src/main/java/com/mckimquyen/reader/infrastructure/ai/OracleEvidenceFinder.kt").takeIf { it.exists() }?.readLines().orEmpty()
        .filterNot {
            val t = it.trimStart()
            t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ")
        }.joinToString("\n")

    @Test
    fun sourceNeverReachesForTheNetworkGeminiOrBodies() {
        listOf("AiRequestGateway", "GeminiSummaryService", "OkHttp", "rawDescription", "fullContent", "queryArticleBody", "http").forEach {
            assertFalse("OracleEvidenceFinder must not mention '$it'", source.contains(it, ignoreCase = true))
        }
        assertTrue(source.contains("queryRecentArticleListWithFeed"))
    }
}
