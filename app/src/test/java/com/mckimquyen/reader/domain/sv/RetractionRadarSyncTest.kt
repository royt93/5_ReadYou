package com.mckimquyen.reader.domain.sv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.articlehistory.ArticleHistoryRepository
import com.mckimquyen.reader.infrastructure.articlehistory.RetractionRadarService
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.filter.SmartFilterManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RetractionRadarSyncTest {

    private class TestRadar(
        dao: com.mckimquyen.reader.domain.repository.ArticleRadarDao,
        history: ArticleHistoryRepository,
        var enabled: Boolean = true,
    ) : RetractionRadarService(ApplicationProvider.getApplicationContext(), dao, history) {
        override fun isEnabled() = enabled
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }

    private lateinit var db: AndroidDatabase
    private lateinit var rss: RssHelper
    private lateinit var radar: TestRadar
    private lateinit var service: LocalRssSv
    private val feed = Feed(id = "f", name = "F", url = "https://e.com/f", groupId = "g", accountId = 1)

    /** What the "network" returns for the next fetch; null means the fetch failed (RssHelper swallows to empty). */
    private var remote: List<Article> = emptyList()
    private var calls = mutableListOf<String?>()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "A", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g", name = "G", accountId = 1))
        db.feedDao().insert(feed)
        rss = mockk(relaxed = true)
        coEvery { rss.queryRssXml(any(), any()) } answers {
            val latest = secondArg<String?>()
            calls += latest
            // Mimic the real parser: stop at the first entry equal to latestLink.
            remote.takeWhile { latest == null || it.link != latest }
        }
        radar = TestRadar(db.articleRadarDao(), ArticleHistoryRepository(db.articleRadarDao()))
        val filters = mockk<SmartFilterManager> { every { applyRules(any()) } answers { firstArg() } }
        service = LocalRssSv(
            context = ApplicationProvider.getApplicationContext(), articleDao = db.articleDao(),
            feedDao = db.feedDao(), rssHelper = rss, notificationHelper = mockk(relaxed = true),
            accountDao = db.accountDao(), groupDao = db.groupDao(), feedHealthDao = db.feedHealthDao(),
            ioDispatcher = Dispatchers.IO, defaultDispatcher = Dispatchers.Default,
            workManager = mockk(relaxed = true), watchdogManager = mockk(relaxed = true), smartFilterManager = filters,
            webFeedScraper = mockk(relaxed = true), smartCollectionMatcher = mockk(relaxed = true),
            widgetRefreshNotifier = mockk(relaxed = true), retractionRadar = radar,
        )
    }

    @After
    fun tearDown() = db.close()

    private fun entry(n: Int, title: String = "Headline $n", body: String = "Body of article $n", day: Long = n.toLong()) = Article(
        id = "1$${UUID.randomUUID()}", date = Date(day * DAY), title = title, rawDescription = body,
        shortDescription = body, link = "https://e.com/a$n", feedId = "f", accountId = 1,
    )

    /** One full retry-sync pass: fetch, insert new ones. */
    private suspend fun syncOnce() {
        assertTrue(service.retryFeedSync(feed))
    }

    private fun articleCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM article").use { it.moveToFirst(); it.getInt(0) }

    private suspend fun idOf(n: Int) = db.articleRadarDao().articleIdByLink("https://e.com/a$n", "f", 1)
    private suspend fun stateOf(n: Int) = idOf(n)?.let { db.articleRadarDao().state(it) }

    @Test
    fun newArticles_getBaselineSnapshotAndNoFlag() = runBlocking {
        remote = listOf(entry(3), entry(2), entry(1))
        syncOnce()
        assertEquals(1, db.articleRadarDao().snapshotCount(idOf(1)!!))
        assertNull(stateOf(1))
    }

    @Test
    fun editedTitleInLaterSync_isDetectedAndStoredWithoutDuplicatingTheArticle() = runBlocking {
        remote = listOf(entry(3), entry(2), entry(1))
        syncOnce()
        remote = listOf(entry(3), entry(2, title = "Headline 2 (corrected)"), entry(1))
        syncOnce()

        assertEquals(1, stateOf(2)?.editCount)
        assertEquals(2, db.articleRadarDao().snapshotCount(idOf(2)!!))
        assertEquals(3, articleCount())
        assertNull(stateOf(1))
    }

    @Test
    fun articleMissingForThreeValidSyncs_isFlaggedRemoved_notBeforeAndAbsentArticleStaysStored() = runBlocking {
        remote = listOf(entry(4), entry(3), entry(2), entry(1))
        syncOnce()

        remote = listOf(entry(4), entry(2), entry(1)) // a3 retracted, still inside the date window
        syncOnce()
        assertEquals(1, stateOf(3)?.missedSyncs)
        assertNull(stateOf(3)?.removedAt)
        syncOnce()
        assertNull(stateOf(3)?.removedAt)
        syncOnce()

        assertNotNull(stateOf(3)?.removedAt)
        assertNotNull("the stored article must survive", idOf(3))
        assertNull(stateOf(2)?.removedAt)
    }

    @Test
    fun emptyOrFailedFetch_neverCountsAsMissing() = runBlocking {
        remote = listOf(entry(3), entry(2), entry(1))
        syncOnce()

        remote = emptyList() // RssHelper swallows errors into an empty list
        repeat(5) { syncOnce() }

        assertNull(stateOf(2))
        assertNull(stateOf(1))
    }

    @Test
    fun articlesOlderThanTheFeedWindow_areNotCountedAsRemoved() = runBlocking {
        remote = listOf(entry(5), entry(4), entry(3), entry(2), entry(1))
        syncOnce()

        remote = listOf(entry(6), entry(5), entry(4), entry(3)) // feed trimmed its oldest two
        repeat(4) { syncOnce() }

        assertNull(stateOf(1)?.removedAt)
        assertNull(stateOf(2)?.removedAt)
    }

    @Test
    fun reappearingArticle_clearsTheRemovalProgress() = runBlocking {
        remote = listOf(entry(4), entry(3), entry(2), entry(1))
        syncOnce()
        remote = listOf(entry(4), entry(2), entry(1))
        repeat(2) { syncOnce() }
        assertEquals(2, stateOf(3)?.missedSyncs)

        remote = listOf(entry(4), entry(3), entry(2), entry(1))
        syncOnce()
        assertEquals(0, stateOf(3)?.missedSyncs)
        assertNull(stateOf(3)?.removedAt)
    }

    @Test
    fun radarOff_keepsTheOldIncrementalFetchAndRecordsNothing() = runBlocking {
        radar.enabled = false
        remote = listOf(entry(2), entry(1))
        syncOnce()
        remote = listOf(entry(3), entry(2), entry(1))
        syncOnce()

        assertEquals(listOf<String?>(null, "https://e.com/a2"), calls)
        assertEquals(3, articleCount())
        assertEquals(0, db.articleRadarDao().snapshotCount(idOf(1)!!))
    }

    @Test
    fun radarOn_insertsOnlyEntriesNewerThanTheLatestStoredOne() = runBlocking {
        remote = listOf(entry(3), entry(2), entry(1))
        syncOnce()
        // An old entry the user archived away must not be re-inserted just because the whole feed is read now.
        db.articleDao().deleteByFeedId(1, "f")
        remote = listOf(entry(4), entry(3), entry(2), entry(1))
        db.articleDao().insert(entry(3))
        syncOnce()

        assertNotNull(idOf(4))
        assertNull(idOf(2))
        assertNull(idOf(1))
    }

    @Test
    fun radarStorageFailure_doesNotFailTheSyncOrLoseArticles() = runBlocking {
        val brokenDao = mockk<com.mckimquyen.reader.domain.repository.ArticleRadarDao>(relaxed = true) {
            coEvery { articleIdByLink(any(), any(), any()) } throws IllegalStateException("disk full")
            coEvery { existingIds(any()) } throws IllegalStateException("disk full")
            coEvery { insertSnapshot(any()) } throws IllegalStateException("disk full")
            coEvery { latestHashes(any()) } throws IllegalStateException("disk full")
            coEvery { trimSnapshots(any()) } throws IllegalStateException("disk full")
        }
        radar = TestRadar(brokenDao, ArticleHistoryRepository(brokenDao))
        service = LocalRssSv(
            context = ApplicationProvider.getApplicationContext(), articleDao = db.articleDao(),
            feedDao = db.feedDao(), rssHelper = rss, notificationHelper = mockk(relaxed = true),
            accountDao = db.accountDao(), groupDao = db.groupDao(), feedHealthDao = db.feedHealthDao(),
            ioDispatcher = Dispatchers.IO, defaultDispatcher = Dispatchers.Default,
            workManager = mockk(relaxed = true), watchdogManager = mockk(relaxed = true),
            smartFilterManager = mockk { every { applyRules(any()) } answers { firstArg() } },
            webFeedScraper = mockk(relaxed = true), smartCollectionMatcher = mockk(relaxed = true),
            widgetRefreshNotifier = mockk(relaxed = true), retractionRadar = radar,
        )
        remote = listOf(entry(2), entry(1))
        syncOnce()
        remote = listOf(entry(3), entry(2), entry(1))
        syncOnce()
        service.clearKeepArchivedArticles()

        assertEquals(3, articleCount())
    }
}
