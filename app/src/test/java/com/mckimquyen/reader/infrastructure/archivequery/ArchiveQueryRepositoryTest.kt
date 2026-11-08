package com.mckimquyen.reader.infrastructure.archivequery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryRange
import com.mckimquyen.reader.domain.archivequery.ParsedArchiveQuery
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleBody
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ArchiveQueryRepositoryTest {
    // Room được tạo trong @Before, chỉ dùng sau khi setUp hoàn tất.
    private lateinit var db: AndroidDatabase
    private lateinit var repository: ArchiveQueryRepository

    private val now = Instant.parse("2026-11-07T10:00:00Z")
    private val range = ArchiveQueryRange(Instant.parse("2026-01-01T00:00:00Z"), now)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        for (account in listOf(ACCOUNT, OTHER_ACCOUNT)) {
            db.accountDao().insert(Account(id = account, name = "Account $account", type = AccountType.Local))
            db.groupDao().insert(Group(id = "group$account", name = "Group", accountId = account))
            db.feedDao().insert(Feed(id = "feed$account", name = "Feed $account", url = "https://example.com/$account", groupId = "group$account", accountId = account))
        }
        repository = ArchiveQueryRepository(
            articleDao = db.articleDao(),
            searchEngine = SemanticSearchEngine(db.articleEmbeddingDao()),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After fun tearDown() { db.close() }

    private fun at(year: Int, month: Int, day: Int): Date =
        Date.from(LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC))

    private suspend fun insert(
        id: String,
        date: Date,
        title: String = "Interest rates policy $id",
        body: String = "<p>Interest rates are discussed in this article $id.</p>",
        account: Int = ACCOUNT,
        full: String? = body,
    ) = db.articleDao().insert(
        Article(
            id = id, title = title, date = date, rawDescription = body, fullContent = full,
            shortDescription = title, link = "https://example.com/$id", feedId = "feed$account", accountId = account,
        )
    )

    private fun parsed(topic: String = "interest rates") = ParsedArchiveQuery(topic, null, false)

    private suspend fun search(topic: String = "interest rates", account: Int = ACCOUNT) =
        repository.search(account, parsed(topic), range, now, ZoneOffset.UTC)

    @Test fun emptyArchive_returnsEveryMonthWithZeroCount() = runBlocking {
        val result = search()
        assertEquals(0, result.totalRelevantArticles)
        assertEquals(11, result.months.size) // Jan..Nov 2026
        assertTrue(result.months.all { it.month.articleCount == 0 && it.evidence.isEmpty() })
        assertTrue(result.shifts.isEmpty())
    }

    @Test fun monthsAreChronologicalAndKeepEmptyGaps() = runBlocking {
        insert("jan", at(2026, 1, 10))
        insert("mar", at(2026, 3, 10))
        val months = search().months
        assertEquals((1..11).toList(), months.map { it.month.month })
        assertEquals(1, months[0].month.articleCount)
        assertEquals(0, months[1].month.articleCount)
        assertEquals(1, months[2].month.articleCount)
    }

    @Test fun irrelevantArticles_areNotCounted() = runBlocking {
        insert("rel", at(2026, 5, 5))
        insert("irr", at(2026, 5, 6), title = "Zebra migration photography", body = "<p>Zebras walk.</p>")
        val may = search().months.single { it.month.month == 5 }
        assertEquals(1, may.month.articleCount)
        assertEquals(listOf("rel"), may.month.topCandidates.map { it.articleId })
    }

    @Test fun otherAccount_isNeverRead() = runBlocking {
        insert("mine", at(2026, 5, 5))
        insert("theirs", at(2026, 5, 6), account = OTHER_ACCOUNT, body = "<p>Interest rates SECRET.</p>")
        val result = search()
        assertEquals(1, result.totalRelevantArticles)
        val quotes = result.months.flatMap { it.evidence }.map { it.quote }
        assertTrue(quotes.none { it.contains("SECRET") })
    }

    @Test fun rangeBoundaries_startInclusiveEndExclusive() = runBlocking {
        insert("before", Date.from(range.startInclusive.minusMillis(1)))
        insert("start", Date.from(range.startInclusive))
        insert("end", Date.from(range.endExclusive))
        assertEquals(1, search().totalRelevantArticles)
    }

    @Test fun oldestPageRelevantArticle_isFoundBeyondFirstPage() = runBlocking {
        // 450 irrelevant newer rows push the relevant January article onto a later 200-row page.
        repeat(450) { i ->
            insert("noise$i", Date.from(Instant.parse("2026-06-01T00:00:00Z").plusSeconds(i.toLong())),
                title = "Zebra migration $i", body = "<p>Zebras.</p>")
        }
        insert("old", at(2026, 1, 3))
        val result = search()
        assertEquals(1, result.totalRelevantArticles)
        assertEquals(listOf("old"), result.months.single { it.month.month == 1 }.month.topCandidates.map { it.articleId })
    }

    @Test fun overOneThousandRows_oldestRelevantArticleStillFoundAcrossSixPages() = runBlocking {
        // 1,001 newer irrelevant rows = 6 pages of 200, so the January article sits on the last page.
        db.runInTransaction {
            runBlocking {
                repeat(1_001) { i ->
                    insert("noise$i", Date.from(Instant.parse("2026-06-01T00:00:00Z").plusSeconds(i.toLong())),
                        title = "Zebra migration $i", body = "<p>Zebras.</p>")
                }
            }
        }
        insert("old", at(2026, 1, 3))
        val result = search()
        assertEquals(1, result.totalRelevantArticles)
        assertEquals(listOf("old"), result.months.single { it.month.month == 1 }.month.topCandidates.map { it.articleId })
    }

    @Test fun moreThanFiftyRelevantArticles_areAllCounted() = runBlocking {
        repeat(80) { i -> insert("r$i", Date.from(Instant.parse("2026-04-01T00:00:00Z").plusSeconds(i.toLong()))) }
        val april = search().months.single { it.month.month == 4 }
        assertEquals(80, april.month.articleCount) // not truncated by the engine's default limit of 50
        assertEquals(3, april.month.topCandidates.size)
    }

    @Test fun evidence_isVerbatimSubstringOfBodyAndLinksToArticle() = runBlocking {
        insert("a", at(2026, 5, 5), body = "<p>Intro line here. The central bank raised interest rates again.</p>")
        val evidence = search().months.single { it.month.month == 5 }.evidence.single()
        assertEquals("a", evidence.articleId)
        assertTrue(evidence.topicMatched)
        assertEquals("The central bank raised interest rates again.", evidence.quote)
    }

    @Test fun blankFullContent_fallsBackToRawDescription() = runBlocking {
        insert("a", at(2026, 5, 5), body = "<p>Raw text about interest rates.</p>", full = "   ")
        val evidence = search().months.single { it.month.month == 5 }.evidence.single()
        assertEquals("Raw text about interest rates.", evidence.quote)
    }

    @Test fun oversizedBody_isSkippedWithoutQueryingItsText() = runBlocking {
        val huge = "<p>" + "interest rates ".repeat(100_000) + "</p>" // > 1 MiB
        insert("huge", at(2026, 5, 5), body = huge)
        val result = search()
        val may = result.months.single { it.month.month == 5 }
        assertEquals(1, may.month.articleCount)
        assertTrue(may.evidence.isEmpty())
        assertEquals(listOf("huge"), result.oversizedArticleIds)
    }

    @Test fun combinedRawAndFullBodySize_overOneMiB_isSkipped() = runBlocking {
        val halfMb = "x".repeat(600 * 1024)
        insert("combined-large", at(2026, 5, 5), body = halfMb, full = halfMb)
        val result = search()
        assertEquals(listOf("combined-large"), result.oversizedArticleIds)
        assertTrue(result.months.single { it.month.month == 5 }.evidence.isEmpty())
    }

    @Test fun bodyReads_areCappedAtThreePerMonth() = runBlocking {
        repeat(10) { i -> insert("r$i", Date.from(Instant.parse("2026-04-01T00:00:00Z").plusSeconds(i.toLong()))) }
        val april = search().months.single { it.month.month == 4 }
        assertTrue(april.evidence.size <= 3)
        assertEquals(3, april.month.topCandidates.size)
    }

    @Test fun shift_isReportedOnlyForAdjacentMonthsWithEnoughArticles() = runBlocking {
        insert("m1a", at(2026, 3, 5), body = "<p>Analysts expect interest rates to increase.</p>")
        insert("m1b", at(2026, 3, 6), body = "<p>Interest rates and savings.</p>")
        insert("m2a", at(2026, 4, 5), body = "<p>The bank will decrease interest rates.</p>")
        insert("m2b", at(2026, 4, 6), body = "<p>Interest rates and loans.</p>")
        val shift = requireNotNull(search().shifts.singleOrNull())
        assertEquals("increase", shift.firstCue)
        assertEquals("decrease", shift.secondCue)
    }

    @Test fun shift_isNotReportedAcrossAnEmptyMonth() = runBlocking {
        insert("m1a", at(2026, 3, 5), body = "<p>Interest rates increase.</p>")
        insert("m1b", at(2026, 3, 6), body = "<p>Interest rates and savings.</p>")
        insert("m3a", at(2026, 5, 5), body = "<p>Interest rates decrease.</p>")
        insert("m3b", at(2026, 5, 6), body = "<p>Interest rates and loans.</p>")
        assertTrue(search().shifts.isEmpty())
    }

    @Test fun blankTopic_returnsNoRelevantArticles() = runBlocking {
        insert("a", at(2026, 5, 5))
        assertEquals(0, search(topic = "   ").totalRelevantArticles)
    }

    @Test fun articleDeletedBeforeBodyRead_losesOnlyItsOwnQuote() {
        runBlocking {
            insert("gone", at(2026, 5, 5), body = "<p>Interest rates gone article.</p>")
            insert("kept", at(2026, 5, 6), body = "<p>Interest rates kept article.</p>")
        }
        val realDao = db.articleDao()
        // Simulates a sync/delete landing between the metadata scan and the body read.
        val racingDao = object : ArticleDao by realDao {
            override suspend fun queryArticleBodyLengths(accountId: Int, articleId: String) =
                if (articleId == "gone") null else realDao.queryArticleBodyLengths(accountId, articleId)
        }
        val racing = ArchiveQueryRepository(racingDao, SemanticSearchEngine(db.articleEmbeddingDao()), Dispatchers.Unconfined)
        val may = runBlocking { racing.search(ACCOUNT, parsed(), range, now, ZoneOffset.UTC) }
            .months.single { it.month.month == 5 }
        assertEquals(2, may.month.articleCount)
        assertEquals(listOf("kept"), may.evidence.map { it.articleId })
    }

    @Test fun bodyReadFailure_doesNotFailTheWholeSearch() {
        runBlocking { insert("a", at(2026, 5, 5)) }
        val realDao = db.articleDao()
        val failingDao = object : ArticleDao by realDao {
            override suspend fun queryArticleBody(accountId: Int, articleId: String): ArticleBody? =
                throw IllegalStateException("Incomplete article body: $articleId")
        }
        val failing = ArchiveQueryRepository(failingDao, SemanticSearchEngine(db.articleEmbeddingDao()), Dispatchers.Unconfined)
        val result = runBlocking { failing.search(ACCOUNT, parsed(), range, now, ZoneOffset.UTC) }
        assertEquals(1, result.totalRelevantArticles)
        assertTrue(result.months.all { it.evidence.isEmpty() })
    }

    @Test fun bodyWithoutTopicWord_isQuotedAsFallbackNotAsMatch() = runBlocking {
        insert("a", at(2026, 5, 5), body = "<p>The weather was calm throughout the whole afternoon.</p>")
        val evidence = search().months.single { it.month.month == 5 }.evidence.single()
        assertEquals(false, evidence.topicMatched)
    }

    @Test fun searchCancelledBetweenPages_endsWithCancellationAndNoResult() {
        runBlocking { repeat(450) { i -> insert("r$i", Date.from(Instant.parse("2026-06-01T00:00:00Z").plusSeconds(i.toLong()))) } }
        var pagesRead = 0
        var jobToCancel: Job? = null
        val realDao = db.articleDao()
        // Cancels the caller right after the first 200-row page, so a second page must never be requested.
        val cancellingDao = object : ArticleDao by realDao {
            override suspend fun queryArticleMetadataPage(
                accountId: Int, sinceMillis: Long, untilMillis: Long, beforeDate: Long?, beforeId: String?, limit: Int,
            ): List<ArticleListItemWithFeed> {
                pagesRead++
                val page = realDao.queryArticleMetadataPage(accountId, sinceMillis, untilMillis, beforeDate, beforeId, limit)
                jobToCancel?.cancel()
                return page
            }
        }
        val cancelling = ArchiveQueryRepository(cancellingDao, SemanticSearchEngine(db.articleEmbeddingDao()), Dispatchers.Unconfined)
        var outcome: Result<*>? = null
        runBlocking {
            val job = launch(Dispatchers.Unconfined, start = CoroutineStart.LAZY) {
                outcome = runCatching { cancelling.search(ACCOUNT, parsed(), range, now, ZoneOffset.UTC) }
            }
            jobToCancel = job
            job.start()
            job.join()
        }
        val failure = requireNotNull(outcome).exceptionOrNull()
        assertTrue("expected CancellationException but was $failure", failure is CancellationException)
        assertEquals(1, pagesRead)
    }

    private companion object {
        const val ACCOUNT = 7
        const val OTHER_ACCOUNT = 8
    }
}
