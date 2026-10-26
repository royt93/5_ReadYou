package com.mckimquyen.reader.infrastructure.articlehistory

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.radar.RemovalUpdate
import com.mckimquyen.reader.domain.radar.RetractionVerdict
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
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

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ArticleHistoryRepositoryTest {

    private lateinit var db: AndroidDatabase
    private lateinit var repo: ArticleHistoryRepository

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "A", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g", name = "G", accountId = 1))
        db.feedDao().insert(Feed(id = "f", name = "F", url = "https://e.com/f", groupId = "g", accountId = 1))
        db.articleDao().insert(article("a1", 1_000L))
        repo = ArticleHistoryRepository(db.articleRadarDao())
    }

    @After
    fun tearDown() = db.close()

    private fun article(id: String, date: Long) = Article(
        id = id, date = Date(date), title = "T$id", rawDescription = "d", shortDescription = "d",
        link = "https://e.com/$id", feedId = "f", accountId = 1,
    )

    @Test
    fun firstSight_recordsBaselineWithoutFlag() = runBlocking {
        assertEquals(RetractionVerdict.NEW, repo.record("a1", "Title", "<p>Body</p>", now = 10L))
        assertEquals(1, db.articleRadarDao().snapshotCount("a1"))
        assertNull(db.articleRadarDao().state("a1"))
    }

    @Test
    fun unchangedContent_addsNothing() = runBlocking {
        repo.record("a1", "Title", "<p>Body</p>", now = 10L)
        assertEquals(RetractionVerdict.UNCHANGED, repo.record("a1", "TITLE", "<div>Body</div>", now = 20L))
        assertEquals(1, db.articleRadarDao().snapshotCount("a1"))
    }

    @Test
    fun editedContent_storesSnapshotAndFlags() = runBlocking {
        repo.record("a1", "Title", "<p>Body</p>", now = 10L)
        assertEquals(RetractionVerdict.EDITED, repo.record("a1", "Title", "<p>Body changed</p>", now = 20L))
        assertEquals(RetractionVerdict.EDITED, repo.record("a1", "New title", "<p>Body changed</p>", now = 30L))

        assertEquals(3, db.articleRadarDao().snapshotCount("a1"))
        val state = db.articleRadarDao().state("a1")
        assertEquals(2, state?.editCount)
        assertEquals(30L, state?.lastEditedAt)
        assertEquals("New title", db.articleRadarDao().snapshots("a1", 1).single().title)
    }

    @Test
    fun removalUpdates_persistAndReappearanceClearsFlag() = runBlocking {
        repo.record("a1", "Title", "<p>Body</p>", now = 10L)
        repo.applyRemoval(listOf(RemovalUpdate("a1", 3, true)), now = 50L)
        assertEquals(50L, db.articleRadarDao().state("a1")?.removedAt)
        assertEquals(3, db.articleRadarDao().state("a1")?.missedSyncs)

        repo.applyRemoval(listOf(RemovalUpdate("a1", 0, false)), now = 60L)
        assertNull(db.articleRadarDao().state("a1")?.removedAt)
        assertEquals(0, db.articleRadarDao().state("a1")?.missedSyncs)
    }

    @Test
    fun removalKeepsEditCount() = runBlocking {
        repo.record("a1", "Title", "<p>Body</p>", now = 10L)
        repo.record("a1", "Title", "<p>Other</p>", now = 20L)
        repo.applyRemoval(listOf(RemovalUpdate("a1", 3, true)), now = 50L)
        assertEquals(1, db.articleRadarDao().state("a1")?.editCount)
    }

    @Test
    fun bodyIsCapped() = runBlocking {
        repo.record("a1", "Title", "x".repeat(200_000), now = 10L)
        val text = db.articleRadarDao().snapshots("a1", 1).single().contentText
        assertTrue(text.length <= com.mckimquyen.reader.domain.radar.SnapshotNormalizer.MAX_SNAPSHOT_CHARS)
    }

    @Test
    fun purge_keepsOriginalPlusNewestAndDropsMiddle() = runBlocking {
        (1..10).forEach { repo.record("a1", "Title", "<p>Body $it</p>", now = it * 10L) }
        assertEquals(10, db.articleRadarDao().snapshotCount("a1"))

        repo.purge(now = 1_000L)

        val kept = db.articleRadarDao().snapshotCount("a1")
        assertEquals(ArticleHistoryRepository.KEEP_LATEST_SNAPSHOTS + 1, kept)
        assertEquals("Body 1", db.articleRadarDao().firstSnapshot("a1")?.contentText)
        assertEquals("Body 10", db.articleRadarDao().snapshots("a1", 1).single().contentText)
    }

    @Test
    fun purge_dropsSnapshotsAndFlagOfLongRemovedArticles() = runBlocking {
        repo.record("a1", "Title", "<p>Body</p>", now = 10L)
        val day = 24L * 60 * 60 * 1000
        repo.applyRemoval(listOf(RemovalUpdate("a1", 3, true)), now = 100L)

        repo.purge(now = 100L + ArticleHistoryRepository.REMOVED_RETENTION_DAYS * day - 1)
        assertNotNull(db.articleRadarDao().state("a1"))
        assertEquals(1, db.articleRadarDao().snapshotCount("a1"))

        repo.purge(now = 100L + ArticleHistoryRepository.REMOVED_RETENTION_DAYS * day + 1)
        assertNull(db.articleRadarDao().state("a1"))
        assertEquals(0, db.articleRadarDao().snapshotCount("a1"))
    }

    @Test
    fun deletingArticleCascades() = runBlocking {
        repo.record("a1", "Title", "<p>Body</p>", now = 10L)
        repo.record("a1", "Title", "<p>Other</p>", now = 20L)
        db.articleDao().deleteByFeedId(1, "f")
        assertEquals(0, db.articleRadarDao().snapshotCount("a1"))
        assertNull(db.articleRadarDao().state("a1"))
    }
}
