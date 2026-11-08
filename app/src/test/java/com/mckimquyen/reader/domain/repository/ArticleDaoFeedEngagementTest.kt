package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.autopilot.FeedEngagementStat
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ArticleDaoFeedEngagementTest {
    // Room được tạo trong @Before, chỉ dùng sau khi setUp hoàn tất.
    private lateinit var db: AndroidDatabase

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        for (account in listOf(ACCOUNT, OTHER_ACCOUNT)) {
            db.accountDao().insert(Account(id = account, name = "Account $account", type = AccountType.Local))
            db.groupDao().insert(Group(id = "group$account", name = "Group", accountId = account))
            db.feedDao().insert(Feed(id = "feedA$account", name = "A", url = "https://example.com/a$account", groupId = "group$account", accountId = account))
            db.feedDao().insert(Feed(id = "feedB$account", name = "B", url = "https://example.com/b$account", groupId = "group$account", accountId = account))
        }
    }

    @After fun tearDown() { db.close() }

    private suspend fun insert(
        id: String,
        feed: String,
        updateAt: Long?,
        account: Int = ACCOUNT,
        readAt: Long? = null,
        starred: Boolean = false,
        readLater: Boolean = false,
        unread: Boolean = true,
        body: String = "",
    ) = db.articleDao().insert(
        Article(
            id = id, title = id, date = Date(updateAt ?: 0L), rawDescription = body, fullContent = body,
            shortDescription = "Summary $id", link = "https://example.com/$id", feedId = feed, accountId = account,
            isUnread = unread, isStarred = starred, isReadLater = readLater,
            updateAt = updateAt?.let(::Date), readAt = readAt,
        )
    )

    private suspend fun stats(since: Long = SINCE, account: Int = ACCOUNT): Map<String, FeedEngagementStat> =
        db.articleDao().queryFeedEngagementStats(account, since).associateBy { it.feedId }

    @Test fun windowCountsOnlyArticlesAtOrAfterTheSinceMoment() = runBlocking {
        insert("old", "feedA$ACCOUNT", SINCE - 1)
        insert("edge", "feedA$ACCOUNT", SINCE)
        insert("new", "feedA$ACCOUNT", SINCE + 1)
        assertEquals(2, stats().getValue("feedA$ACCOUNT").windowTotal)
    }

    @Test fun oldestUpdateAtLooksAtEveryStoredArticleNotJustTheWindow() = runBlocking {
        insert("ancient", "feedA$ACCOUNT", SINCE - 50)
        insert("recent", "feedA$ACCOUNT", SINCE + 5)
        val stat = stats().getValue("feedA$ACCOUNT")
        assertEquals(1, stat.windowTotal)
        assertEquals(SINCE - 50, stat.oldestUpdateAtMillis)
    }

    @Test fun engagementCountsReadAtStarredAndReadLaterEachOnce() = runBlocking {
        insert("opened", "feedA$ACCOUNT", SINCE, readAt = 123L)
        insert("starred", "feedA$ACCOUNT", SINCE, starred = true)
        insert("later", "feedA$ACCOUNT", SINCE, readLater = true)
        insert("all three", "feedA$ACCOUNT", SINCE, readAt = 5L, starred = true, readLater = true)
        insert("none", "feedA$ACCOUNT", SINCE)
        insert("markedReadOnly", "feedA$ACCOUNT", SINCE, unread = false)
        val stat = stats().getValue("feedA$ACCOUNT")
        assertEquals(6, stat.windowTotal)
        assertEquals(4, stat.windowEngaged) // an article with all three flags still counts once
    }

    @Test fun engagedArticlesOutsideTheWindowAreNotCounted() = runBlocking {
        insert("oldEngaged", "feedA$ACCOUNT", SINCE - 1, starred = true)
        insert("inside", "feedA$ACCOUNT", SINCE)
        assertEquals(0, stats().getValue("feedA$ACCOUNT").windowEngaged)
    }

    @Test fun statsAreGroupedPerFeed() = runBlocking {
        insert("a1", "feedA$ACCOUNT", SINCE)
        insert("b1", "feedB$ACCOUNT", SINCE, starred = true)
        insert("b2", "feedB$ACCOUNT", SINCE)
        val result = stats()
        assertEquals(1, result.getValue("feedA$ACCOUNT").windowTotal)
        assertEquals(2, result.getValue("feedB$ACCOUNT").windowTotal)
        assertEquals(1, result.getValue("feedB$ACCOUNT").windowEngaged)
    }

    @Test fun otherAccountsArticlesNeverLeakIn() = runBlocking {
        insert("mine", "feedA$ACCOUNT", SINCE)
        insert("theirs", "feedA$OTHER_ACCOUNT", SINCE, account = OTHER_ACCOUNT, starred = true)
        assertEquals(setOf("feedA$ACCOUNT"), stats().keys)
        assertEquals(setOf("feedA$OTHER_ACCOUNT"), stats(account = OTHER_ACCOUNT).keys)
    }

    @Test fun articleWithoutUpdateAtIsNeverInTheWindowAndGivesNoAge() = runBlocking {
        insert("noTimestamp", "feedA$ACCOUNT", updateAt = null)
        val stat = stats().getValue("feedA$ACCOUNT")
        assertEquals(0, stat.windowTotal)
        assertNull(stat.oldestUpdateAtMillis)
    }

    @Test fun oversizedBodyDoesNotBreakTheAggregate() = runBlocking {
        insert("huge", "feedA$ACCOUNT", SINCE, body = "x".repeat(OVERSIZED_BODY_CHARS))
        assertEquals(1, stats().getValue("feedA$ACCOUNT").windowTotal)
    }

    @Test fun feedWithNoArticlesIsAbsent() = runBlocking {
        assertEquals(emptyMap<String, FeedEngagementStat>(), stats())
    }

    @Test fun starredCountIsPerFeedAndPerAccount() = runBlocking {
        insert("s1", "feedA$ACCOUNT", SINCE, starred = true)
        insert("s2", "feedA$ACCOUNT", SINCE - 500, starred = true) // starred counts regardless of window
        insert("n1", "feedA$ACCOUNT", SINCE)
        insert("other feed", "feedB$ACCOUNT", SINCE, starred = true)
        insert("other account", "feedA$OTHER_ACCOUNT", SINCE, account = OTHER_ACCOUNT, starred = true)
        assertEquals(2, db.articleDao().queryStarredCountByFeedId(ACCOUNT, "feedA$ACCOUNT"))
        assertEquals(1, db.articleDao().queryStarredCountByFeedId(ACCOUNT, "feedB$ACCOUNT"))
        assertEquals(0, db.articleDao().queryStarredCountByFeedId(ACCOUNT, "missing"))
    }

    private companion object {
        const val ACCOUNT = 7
        const val OTHER_ACCOUNT = 8
        const val SINCE = 1_000_000L
        const val OVERSIZED_BODY_CHARS = 1_200 * 1024
    }
}
