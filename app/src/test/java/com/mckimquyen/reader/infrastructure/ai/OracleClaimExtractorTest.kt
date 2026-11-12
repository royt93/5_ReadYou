package com.mckimquyen.reader.infrastructure.ai

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.oracle.OracleMarket
import com.mckimquyen.reader.domain.model.oracle.OracleMarketStatus
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.setOracleClaimsGeminiConsent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OracleClaimExtractorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val gateway: AiRequestGateway = mockk()
    private val summaryService: GeminiSummaryService = mockk()
    private lateinit var db: AndroidDatabase
    private var consented = true

    // 2026-11-12T10:00:00Z
    private val now = LocalDate.of(2026, 11, 12).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 10 * HOUR
    private val day = 24 * HOUR

    private lateinit var extractor: OracleClaimExtractor

    private fun claimJson(on: String = "2026-12-31") =
        """{"hasClaim":true,"question":"Will it ship?","expiresOn":"$on","resolutionCriteria":"Vendor release notes."}"""

    @Before
    fun setUp() = runBlocking {
        context.dataStore.edit { it.clear() }
        ShadowLog.clear()
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "A", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g", name = "G", accountId = 1))
        db.feedDao().insert(Feed(id = "f", name = "F", url = "https://e.com/f", groupId = "g", accountId = 1))
        every { summaryService.resolveApiKeys() } returns listOf("key-a")
        coEvery { gateway.execute(any(), any(), any()) } returns claimJson()
        extractor = object : OracleClaimExtractor(context, db.oracleMarketDao(), gateway, summaryService) {
            override fun isConsented() = consented
            override fun nowMillis() = now
        }
    }

    @After
    fun tearDown() = db.close()

    private fun article(id: String, ageMillis: Long = HOUR, title: String = "T-$id", desc: String = "S-$id") = Article(
        id = id, date = Date(now - ageMillis), title = title, rawDescription = "RAW-$id", shortDescription = desc,
        link = "https://secret.example/$id", feedId = "f", accountId = 1,
    )

    private fun put(vararg a: Article) = runBlocking { db.articleDao().insertOnConflictIgnore(*a) }
    private fun run() = runBlocking { extractor.extractRecent(1) }
    private fun row(id: String) = runBlocking { db.oracleMarketDao().byArticle(id) }

    @Test
    fun consentOff_makesNoRequestAndWritesNothing() {
        put(article("a"))
        consented = false
        val r = run()
        assertEquals(OracleRunResult(0, 0, false), r)
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
        verify(exactly = 0) { summaryService.resolveApiKeys() }
        assertNull(row("a"))
    }

    @Test
    fun realConsentReader_neverAskedAndDeclinedMeanOff_agreedMeansOn() {
        val real = object : OracleClaimExtractor(context, db.oracleMarketDao(), gateway, summaryService) {
            override fun nowMillis() = now
        }
        put(article("a"))
        assertEquals(0, runBlocking { real.extractRecent(1) }.requests)

        runBlocking { context.setOracleClaimsGeminiConsent(false) }
        assertEquals(0, runBlocking { real.extractRecent(1) }.requests)

        runBlocking { context.setOracleClaimsGeminiConsent(true) }
        assertEquals(1, runBlocking { real.extractRecent(1) }.requests)
    }

    @Test
    fun noKey_makesNoRequestAndReportsStopped() {
        put(article("a"))
        every { summaryService.resolveApiKeys() } returns emptyList()
        assertEquals(OracleRunResult(0, 0, true), run())
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun sendsAtMostFiveArticles_newestFirst() {
        put(*(1..8).map { article("a$it", ageMillis = it * HOUR) }.toTypedArray())
        val r = run()
        assertEquals(5, r.requests)
        coVerify(exactly = 5) { gateway.execute("oracle_claim", any(), listOf("key-a")) }
        (1..5).forEach { assertEquals("a$it", row("a$it")?.articleId) }
        (6..8).forEach { assertNull(row("a$it")) }
    }

    @Test
    fun onlyArticlesFromTheLastThreeDays_areConsidered() {
        put(article("fresh", ageMillis = 2 * day), article("stale", ageMillis = 4 * day))
        assertEquals(1, run().requests)
        assertNull(row("stale"))
        assertEquals(OracleMarketStatus.HAS_CLAIM, row("fresh")?.status)
    }

    @Test
    fun requestBody_holdsTheTitleAndDescriptionButNeverTheBodyOrTheLink() {
        put(article("a", title = "Chipmaker plans fab", desc = "<p>Opens next year.</p>"))
        val body = slot<String>()
        coEvery { gateway.execute(any(), capture(body), any()) } returns claimJson()
        run()
        assertTrue(body.captured.contains("Chipmaker plans fab"))
        assertTrue(body.captured.contains("Opens next year."))
        assertFalse(body.captured.contains("RAW-a"))
        assertFalse(body.captured.contains("secret.example"))
        assertFalse(body.captured.contains("<p>"))
    }

    @Test
    fun claim_isStoredWithItsFields() {
        put(article("a"))
        val r = run()
        assertEquals(OracleRunResult(1, 1, false), r)
        val m = row("a")!!
        assertEquals(OracleMarketStatus.HAS_CLAIM, m.status)
        assertEquals("Will it ship?", m.question)
        assertEquals("Vendor release notes.", m.resolutionCriteria)
        assertEquals(LocalDate.of(2026, 12, 31).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1, m.expiresAt)
        assertEquals(now, m.createdAt)
    }

    @Test
    fun noClaim_isRememberedSoItIsNeverAskedAgain() {
        put(article("a"))
        coEvery { gateway.execute(any(), any(), any()) } returns """{"hasClaim":false}"""
        assertEquals(OracleRunResult(1, 0, false), run())
        assertEquals(OracleMarketStatus.NO_CLAIM, row("a")?.status)

        assertEquals(0, run().requests)
        coVerify(exactly = 1) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun decidedArticles_areNotAskedAgain() {
        put(article("a"))
        run()
        assertEquals(0, run().requests)
        coVerify(exactly = 1) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun rejectedAnswer_isPendingThenGivenUpAfterTwoAttempts() {
        put(article("a"))
        coEvery { gateway.execute(any(), any(), any()) } returns "not json"

        run()
        assertEquals(OracleMarketStatus.PENDING, row("a")?.status)
        assertEquals(1, row("a")?.attempts)

        run()
        assertEquals(OracleMarketStatus.NO_CLAIM, row("a")?.status)
        assertEquals(2, row("a")?.attempts)

        assertEquals(0, run().requests)
        coVerify(exactly = 2) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun aPendingArticleThatLaterSucceeds_becomesAClaim() {
        put(article("a"))
        coEvery { gateway.execute(any(), any(), any()) } returnsMany listOf("not json", claimJson())
        run()
        run()
        assertEquals(OracleMarketStatus.HAS_CLAIM, row("a")?.status)
    }

    @Test
    fun emptyInput_isNoClaimWithoutARequest() {
        put(article("a", title = " ", desc = "<br>"))
        val r = run()
        assertEquals(0, r.requests)
        assertEquals(OracleMarketStatus.NO_CLAIM, row("a")?.status)
    }

    private fun stopsTheWholeRunOn(error: Throwable) {
        put(article("a", ageMillis = 1 * HOUR), article("b", ageMillis = 2 * HOUR), article("c", ageMillis = 3 * HOUR))
        coEvery { gateway.execute(any(), any(), any()) } throws error
        val r = run()
        assertEquals("one failed request ends the run", 1, r.requests)
        assertTrue(r.stopped)
        assertNull(row("a"))
        assertNull(row("b"))
        coVerify(exactly = 1) { gateway.execute(any(), any(), any()) }
    }

    @Test fun rateLimited_stopsTheRun() = stopsTheWholeRunOn(GeminiSummaryService.SummaryException.RateLimited)
    @Test fun invalidKey_stopsTheRun() = stopsTheWholeRunOn(GeminiSummaryService.SummaryException.InvalidApiKey)
    @Test fun missingKey_stopsTheRun() = stopsTheWholeRunOn(GeminiSummaryService.SummaryException.MissingApiKey)
    @Test fun network_stopsTheRun() = stopsTheWholeRunOn(GeminiSummaryService.SummaryException.Network)
    @Test fun ioError_stopsTheRun() = stopsTheWholeRunOn(IOException("boom"))

    @Test
    fun cancellation_isRethrown_andWritesNothing() {
        put(article("a"))
        coEvery { gateway.execute(any(), any(), any()) } throws CancellationException("cancelled")
        val thrown = runCatching { run() }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        assertNull(row("a"))
    }

    @Test
    fun logs_neverContainTheTitleTheDescriptionOrTheAnswer() {
        put(article("a", title = "SecretTitleXyz", desc = "SecretDescXyz"))
        coEvery { gateway.execute(any(), any(), any()) } returns "SecretAnswerXyz not json"
        run()
        coEvery { gateway.execute(any(), any(), any()) } throws IOException("SecretTitleXyz leaked in message")
        put(article("b", title = "SecretTitleXyz", desc = "SecretDescXyz"))
        run()
        val log = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable?.message.orEmpty()}" }
        assertFalse(log.contains("SecretTitleXyz"))
        assertFalse(log.contains("SecretDescXyz"))
        assertFalse(log.contains("SecretAnswerXyz"))
    }

    @Test
    fun cleanup_runsEvenWhenConsentIsOff_andKeepsFreshRows() {
        put(article("old"), article("fresh"))
        runBlocking {
            db.oracleMarketDao().upsert(OracleMarket(articleId = "old", status = OracleMarketStatus.NO_CLAIM, createdAt = now - 31 * day))
            db.oracleMarketDao().upsert(OracleMarket(articleId = "fresh", status = OracleMarketStatus.NO_CLAIM, createdAt = now - 29 * day))
        }
        consented = false
        run()
        assertNull(row("old"))
        assertEquals(OracleMarketStatus.NO_CLAIM, row("fresh")?.status)
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
