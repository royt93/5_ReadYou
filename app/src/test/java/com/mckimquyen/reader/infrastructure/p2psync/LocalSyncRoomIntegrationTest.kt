package com.mckimquyen.reader.infrastructure.p2psync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleReadPosition
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.p2psync.FeedUrlNormalizer
import com.mckimquyen.reader.domain.p2psync.SyncArticleState
import com.mckimquyen.reader.domain.p2psync.SyncFeed
import com.mckimquyen.reader.domain.p2psync.SyncPackage
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.ui.ext.getDefaultGroupId
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
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LocalSyncRoomIntegrationTest {
    private lateinit var db: AndroidDatabase
    private lateinit var repository: LocalSyncRepository
    private val incoming = SyncPackage(
        1, "a".repeat(32), 10,
        listOf(
            SyncFeed("https://example.org/rss", "Sender Name", "Sender Group"),
            SyncFeed("https://new.org/rss", "New Feed", "New Group"),
            SyncFeed("https://fever.org/rss", "Fever copy", "Fever Group"),
        ),
        listOf(
            SyncArticleState("https://example.org/rss", "https://example.org/a", false, true, 50L),
            SyncArticleState("https://example.org/rss", "https://example.org/missing", false, true, 60L),
            SyncArticleState("https://fever.org/rss", "https://example.org/fever", false, true, 70L),
        ),
    )

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "Local", type = AccountType.Local))
        db.accountDao().insert(Account(id = 2, name = "Other Local", type = AccountType.Local))
        db.accountDao().insert(Account(id = 3, name = "Fever", type = AccountType.Fever))
        db.groupDao().insert(
            Group(id = 1.getDefaultGroupId(), name = "Default", accountId = 1),
            Group(id = "mygroup", name = "My group", accountId = 1),
            Group(id = 2.getDefaultGroupId(), name = "Default", accountId = 2),
            Group(id = 3.getDefaultGroupId(), name = "Default", accountId = 3),
        )
        db.feedDao().insert(
            Feed("local-feed", "Receiver Name", url = "https://EXAMPLE.org:443/rss/#fragment", groupId = "mygroup", accountId = 1, icon = "local-icon"),
            Feed("other-feed", "Other account copy", url = "https://EXAMPLE.org:443/rss/#fragment", groupId = 2.getDefaultGroupId(), accountId = 2),
            Feed("fever-feed", "Fever", url = "https://fever.org/rss", groupId = 3.getDefaultGroupId(), accountId = 3),
        )
        db.articleDao().insert(
            article("z-local-uuid", "local-feed", 1, "https://example.org/a", unread = true, starred = false, readAt = 100L),
            article("other-local-uuid", "other-feed", 2, "https://example.org/a", unread = true, starred = false, readAt = null),
            article("fever-uuid", "fever-feed", 3, "https://example.org/fever", unread = true, starred = false, readAt = null),
        )
        var nextId = 0
        repository = LocalSyncRepository(db, newId = { "new-feed-id-${nextId++}" })
    }

    @After fun tearDown() = db.close()

    @Test fun duplicateFeedKeepsReceiverMetadataAndNewFeedUsesDefaultGroup() = runBlocking {
        repository.apply(1, incoming)
        val duplicate = db.feedDao().queryAll(1).single { FeedUrlNormalizer.normalize(it.url) == "https://example.org/rss" }
        assertEquals("Receiver Name", duplicate.name)
        assertEquals("mygroup", duplicate.groupId)
        assertEquals("local-icon", duplicate.icon)
        val added = db.feedDao().queryAll(1).single { it.url == "https://new.org/rss" }
        assertEquals(1.getDefaultGroupId(), added.groupId)
        assertEquals("New Feed", added.name)
    }

    @Test fun updatesOnlyReadStarAndReadAtOnMatchedLocalArticle() = runBlocking {
        val before = db.articleDao().queryById("z-local-uuid")!!.article
        repository.apply(1, incoming)
        val after = db.articleDao().queryById("z-local-uuid")!!.article
        assertFalse(after.isUnread)
        assertTrue(after.isStarred)
        assertEquals(50L, after.readAt)
        assertEquals(before.title, after.title)
        assertEquals(before.date, after.date)
        assertEquals(before.feedId, after.feedId)
        assertEquals(before.accountId, after.accountId)
        assertEquals(before.rawDescription, after.rawDescription)
        assertEquals(before.fullContent, after.fullContent)
        assertEquals(before.aiSummary, after.aiSummary)
        assertEquals(before.audioUrl, after.audioUrl)
    }

    @Test fun unmatchedArticleIsSkippedAndBothOtherAccountsAreUntouched() = runBlocking {
        repository.apply(1, incoming)
        assertNull(db.articleDao().queryArticleByLink("https://example.org/missing", "local-feed", 1))
        val otherLocal = db.articleDao().queryById("other-local-uuid")!!.article
        assertTrue(otherLocal.isUnread)
        assertFalse(otherLocal.isStarred)
        assertNull(otherLocal.readAt)
        val fever = db.articleDao().queryById("fever-uuid")!!.article
        assertTrue(fever.isUnread)
        assertFalse(fever.isStarred)
        assertNull(fever.readAt)
    }

    @Test fun applyingTheSamePackageTwiceIsIdempotent() = runBlocking {
        repository.apply(1, incoming)
        val once = db.feedDao().queryAll(1).sortedBy { it.id }
        repository.apply(1, incoming)
        val twice = db.feedDao().queryAll(1).sortedBy { it.id }
        assertEquals(once.map { it.id to it.url }, twice.map { it.id to it.url })
        assertEquals(1, twice.count { FeedUrlNormalizer.normalize(it.url) == "https://example.org/rss" })
    }

    @Test fun databaseFailureAfterFeedInsertRollsBackEveryWrite() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER abort_sync_update BEFORE UPDATE OF isUnread ON article BEGIN SELECT RAISE(ABORT, 'injected'); END",
        )
        assertTrue(runCatching { repository.apply(1, incoming) }.isFailure)
        assertNull(db.feedDao().queryByLink(1, "https://new.org/rss").firstOrNull())
        assertTrue(db.articleDao().queryById("z-local-uuid")!!.article.isUnread)
    }

    @Test fun rejectsNonLocalAccountBeforeAnyWrite() = runBlocking {
        assertTrue(runCatching { repository.apply(3, incoming) }.isFailure)
        assertNull(db.feedDao().queryByLink(3, "https://new.org/rss").firstOrNull())
    }

    @Test fun updateSyncStateRequiresMatchingArticleAccountId() = runBlocking {
        val updated = db.articleDao().updateSyncState(
            accountId = 2,
            articleId = "z-local-uuid",
            isUnread = false,
            isStarred = true,
            readAt = 10L,
        )
        assertEquals(0, updated)
        val article = db.articleDao().queryById("z-local-uuid")!!.article
        assertTrue(article.isUnread)
        assertFalse(article.isStarred)
        assertEquals(100L, article.readAt)
    }

    @Test fun stateApplyDoesNotTouchReadPosition() = runBlocking {
        db.articleReadPositionDao().upsertPosition(
            ArticleReadPosition("z-local-uuid", 1, "https://example.org/rss", "https://example.org/a", 0.4f, 77L, false),
        )
        repository.apply(1, incoming)
        assertEquals(0.4f, db.articleReadPositionDao().getPosition("z-local-uuid")!!.progress, 0f)
    }

    private fun article(id: String, feedId: String, accountId: Int, link: String, unread: Boolean, starred: Boolean, readAt: Long?) = Article(
        id = id, date = Date(123L), title = "Keep title", author = "Keep author", rawDescription = "RAW BODY",
        shortDescription = "SHORT BODY", fullContent = "FULL BODY", img = "https://example.org/img", link = link,
        feedId = feedId, accountId = accountId, isUnread = unread, isStarred = starred, isReadLater = false,
        updateAt = Date(99L), aiSummary = "KEEP SUMMARY", readAt = readAt, audioUrl = "KEEP AUDIO",
        audioMime = "audio/aac", audioDurationSec = 11,
    )
}
