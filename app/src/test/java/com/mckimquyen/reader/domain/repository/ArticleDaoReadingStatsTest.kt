package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.statistics.FeedReadCount
import com.mckimquyen.reader.domain.model.statistics.ReadingDayCount
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.db.MIGRATION_14_15
import com.mckimquyen.reader.infrastructure.db.MIGRATION_15_16
import com.mckimquyen.reader.infrastructure.db.MIGRATION_16_17
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ArticleDaoReadingStatsTest {

    private lateinit var database: AndroidDatabase
    private val dao get() = database.articleDao()

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java
        ).allowMainThreadQueries().addMigrations(MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17).build()
        database.accountDao().insert(Account(id = ACCOUNT_ID, name = "Test", type = AccountType.Local))
        database.groupDao().insert(Group(id = GROUP_ID, name = "Group", accountId = ACCOUNT_ID))
        insertFeed(FEED_ALPHA, "Alpha", GROUP_ID, ACCOUNT_ID)
        insertFeed(FEED_BETA, "Beta", GROUP_ID, ACCOUNT_ID)
        insertFeed(FEED_GAMMA, "Gamma", GROUP_ID, ACCOUNT_ID)
        database.accountDao().insert(Account(id = OTHER_ACCOUNT_ID, name = "Other", type = AccountType.Local))
        database.groupDao().insert(Group(id = OTHER_GROUP_ID, name = "Other group", accountId = OTHER_ACCOUNT_ID))
        insertFeed(FEED_OTHER, "Other feed", OTHER_GROUP_ID, OTHER_ACCOUNT_ID)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun markOpened_writesOnceAndKeepsFirstValue() = runBlocking {
        insertArticle("a1", FEED_ALPHA, readAt = null)

        dao.markOpened(ACCOUNT_ID, "a1", FIRST_READ)
        dao.markOpened(ACCOUNT_ID, "a1", SECOND_READ)

        assertEquals(FIRST_READ, readAtOf("a1"))
    }

    @Test
    fun markOpened_ignoresOtherAccount() = runBlocking {
        insertArticle("a1", FEED_ALPHA, readAt = null)

        dao.markOpened(OTHER_ACCOUNT_ID, "a1", FIRST_READ)

        assertNull(readAtOf("a1"))
    }

    @Test
    fun queryReadingDayCounts_groupsByLocalDayAndExcludesNullOtherAccountAndOld() = runBlocking {
        insertArticle("d10a", FEED_ALPHA, readAt = localMillis(2026, 10, 10))
        insertArticle("d10b", FEED_ALPHA, readAt = localMillis(2026, 10, 10))
        insertArticle("d11", FEED_ALPHA, readAt = localMillis(2026, 10, 11))
        insertArticle("unread-null", FEED_ALPHA, readAt = null)
        insertArticle("other", FEED_OTHER, readAt = localMillis(2026, 10, 10), accountId = OTHER_ACCOUNT_ID)
        insertArticle("old", FEED_ALPHA, readAt = localMillis(2026, 9, 1))

        val rows = dao.queryReadingDayCounts(ACCOUNT_ID, localMillis(2026, 10, 1))

        assertEquals(listOf(ReadingDayCount("2026-10-10", 2), ReadingDayCount("2026-10-11", 1)), rows)
    }

    @Test
    fun queryTotalRead_countsOnlyReadAtNotNullForAccount() = runBlocking {
        insertArticle("d10a", FEED_ALPHA, readAt = localMillis(2026, 10, 10))
        insertArticle("d10b", FEED_ALPHA, readAt = localMillis(2026, 10, 10))
        insertArticle("d11", FEED_ALPHA, readAt = localMillis(2026, 10, 11))
        insertArticle("old", FEED_ALPHA, readAt = localMillis(2026, 9, 1))
        insertArticle("unread-null", FEED_ALPHA, readAt = null)
        insertArticle("other", FEED_OTHER, readAt = localMillis(2026, 10, 10), accountId = OTHER_ACCOUNT_ID)

        assertEquals(4, dao.queryTotalRead(ACCOUNT_ID))
        assertEquals(1, dao.queryTotalRead(OTHER_ACCOUNT_ID))
    }

    @Test
    fun queryTopFeeds_ordersByCountThenNameAndHonoursLimit() = runBlocking {
        repeat(3) { insertArticle("alpha-$it", FEED_ALPHA, readAt = FIRST_READ) }
        repeat(3) { insertArticle("beta-$it", FEED_BETA, readAt = FIRST_READ) }
        insertArticle("gamma-0", FEED_GAMMA, readAt = FIRST_READ)
        insertArticle("gamma-unread", FEED_GAMMA, readAt = null)

        val all = dao.queryTopFeeds(ACCOUNT_ID)
        val limited = dao.queryTopFeeds(ACCOUNT_ID, limit = 2)

        assertEquals(
            listOf(
                FeedReadCount(FEED_ALPHA, "Alpha", 3),
                FeedReadCount(FEED_BETA, "Beta", 3),
                FeedReadCount(FEED_GAMMA, "Gamma", 1),
            ),
            all,
        )
        assertEquals(listOf(FEED_ALPHA, FEED_BETA), limited.map { it.feedId })
    }

    private suspend fun readAtOf(id: String): Long? = dao.queryById(id)?.article?.readAt

    private suspend fun insertFeed(id: String, name: String, groupId: String, accountId: Int) {
        database.feedDao().insert(
            Feed(id = id, name = name, url = "https://example.com/$id", groupId = groupId, accountId = accountId)
        )
    }

    private suspend fun insertArticle(
        id: String,
        feedId: String,
        readAt: Long?,
        accountId: Int = ACCOUNT_ID,
    ) {
        dao.insert(
            Article(
                id = id,
                date = Date(),
                title = id,
                rawDescription = "",
                shortDescription = "",
                link = "https://example.com/$id",
                feedId = feedId,
                accountId = accountId,
                isUnread = false,
                readAt = readAt,
            )
        )
    }

    /** Buổi trưa theo múi giờ JVM mặc định, tránh lệch biên nửa đêm giữa JVM và SQLite localtime. */
    private fun localMillis(year: Int, month: Int, day: Int): Long =
        LocalDateTime.of(year, month, day, NOON_HOUR, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        const val ACCOUNT_ID = 7
        const val OTHER_ACCOUNT_ID = 8
        const val GROUP_ID = "group-1"
        const val OTHER_GROUP_ID = "group-2"
        const val FEED_ALPHA = "feed-alpha"
        const val FEED_BETA = "feed-beta"
        const val FEED_GAMMA = "feed-gamma"
        const val FEED_OTHER = "feed-other"
        const val FIRST_READ = 1_000L
        const val SECOND_READ = 2_000L
        const val NOON_HOUR = 12
    }
}
