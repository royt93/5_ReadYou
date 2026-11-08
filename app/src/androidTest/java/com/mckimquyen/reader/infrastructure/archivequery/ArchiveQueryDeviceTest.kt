package com.mckimquyen.reader.infrastructure.archivequery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryRange
import com.mckimquyen.reader.domain.archivequery.ParsedArchiveQuery
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date

/**
 * On-device integration test for EXC-11: runs against Android's bundled SQLite implementation with an in-memory
 * database to verify that date ranges, Jsoup sentence extraction, semantic ranking, and perspective shifts work on
 * a live Android runtime without touching the user's persistent databases.
 */
@RunWith(AndroidJUnit4::class)
class ArchiveQueryDeviceTest {

    private lateinit var database: AndroidDatabase
    private lateinit var repository: ArchiveQueryRepository

    private val now = Instant.parse("2026-11-08T10:00:00Z")
    private val range = ArchiveQueryRange(Instant.parse("2026-01-01T00:00:00Z"), now)

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AndroidDatabase::class.java,
        ).build()
        database.accountDao().insert(Account(id = ACCOUNT_ID, name = "Local", type = AccountType.Local))
        database.groupDao().insert(Group(id = "group_1", name = "Group", accountId = ACCOUNT_ID))
        database.feedDao().insert(Feed(id = "feed_a", name = "Source A", url = "https://e.com/a", groupId = "group_1", accountId = ACCOUNT_ID))

        repository = ArchiveQueryRepository(
            articleDao = database.articleDao(),
            searchEngine = SemanticSearchEngine(database.articleEmbeddingDao()),
            ioDispatcher = Dispatchers.IO,
        )
    }

    @After fun tearDown() {
        database.close()
    }

    private fun at(year: Int, month: Int, day: Int): Date =
        Date.from(LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC))

    private suspend fun insert(id: String, date: Date, title: String, body: String) {
        database.articleDao().insert(
            Article(
                id = id, title = title, date = date, rawDescription = body, fullContent = body,
                shortDescription = title, link = "https://example.com/$id", feedId = "feed_a", accountId = ACCOUNT_ID,
            )
        )
    }

    @Test
    fun liveDevice_runsEndToEndTimelineEvidenceAndShift() = runBlocking {
        // March: two articles, both lean towards 'increase'
        insert(
            "mar_1", at(2026, 3, 5),
            title = "Central bank moves to increase interest rates",
            body = "<p>Officials met early today. The board confirmed it will increase interest rates this quarter.</p>",
        )
        insert(
            "mar_2", at(2026, 3, 19),
            title = "Lenders see higher interest rates",
            body = "<p>Mortgage costs continue to shift as interest rates reach new levels across the sector.</p>",
        )
        // April: two articles, one with verbatim 'decrease' -> triggers a perspective shift from March
        insert(
            "apr_1", at(2026, 4, 9),
            title = "Markets react as interest rates decrease",
            body = "<p>Trading was volatile this morning. The committee voted to decrease interest rates after fresh data.</p>",
        )
        insert(
            "apr_2", at(2026, 4, 24),
            title = "Borrowers assess new interest rates",
            body = "<p>Borrowers welcomed the move and asked what new interest rates will mean for small loans.</p>",
        )

        val result = repository.search(
            accountId = ACCOUNT_ID,
            parsed = ParsedArchiveQuery("interest rates", null, false),
            range = range,
            now = now,
            zone = ZoneOffset.UTC,
        )

        assertEquals(4, result.totalRelevantArticles)
        val mar = result.months.single { it.month.month == 3 }
        val apr = result.months.single { it.month.month == 4 }
        assertEquals(2, mar.month.articleCount)
        assertEquals(2, apr.month.articleCount)

        // Verbatim quote extraction worked on live Android runtime
        assertTrue(mar.evidence.any { it.quote.contains("increase interest rates") })
        assertTrue(apr.evidence.any { it.quote.contains("decrease interest rates") })

        // Perspective shift between March and April detected on device
        val shift = result.shifts.singleOrNull()
        assertNotNull("Shift must be detected between adjacent March and April", shift)
        assertEquals("increase", shift!!.firstCue.lowercase())
        assertEquals("decrease", shift.secondCue.lowercase())
    }

    private companion object {
        const val ACCOUNT_ID = 1
    }
}
