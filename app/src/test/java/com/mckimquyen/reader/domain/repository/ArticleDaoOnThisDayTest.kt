package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.db.MIGRATION_14_15
import com.mckimquyen.reader.infrastructure.db.MIGRATION_15_16
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class ArticleDaoOnThisDayTest {

    private lateinit var database: AndroidDatabase

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java
        ).allowMainThreadQueries().addMigrations(MIGRATION_14_15, MIGRATION_15_16).build()
        database.accountDao().insert(Account(id = ACCOUNT_ID, name = "Test", type = AccountType.Local))
        database.groupDao().insert(Group(id = GROUP_ID, name = "Group", accountId = ACCOUNT_ID))
        database.feedDao().insert(
            Feed(id = FEED_ID, name = "Feed", url = FEED_URL, groupId = GROUP_ID, accountId = ACCOUNT_ID)
        )
        database.accountDao().insert(Account(id = OTHER_ACCOUNT_ID, name = "Other", type = AccountType.Local))
        database.groupDao().insert(Group(id = OTHER_GROUP_ID, name = "Other group", accountId = OTHER_ACCOUNT_ID))
        database.feedDao().insert(
            Feed(id = OTHER_FEED_ID, name = "Other feed", url = OTHER_FEED_URL, groupId = OTHER_GROUP_ID, accountId = OTHER_ACCOUNT_ID)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun queryOnThisDay_returnsOnlyReadEarlierYearSameDayForAccount() = runBlocking {
        insert("last-year", localDate(2025, 10, 18))
        insert("two-years", localDate(2024, 10, 18))
        insert("this-year", localDate(2026, 10, 18))
        insert("unread", localDate(2025, 10, 18), isUnread = true)
        insert("yesterday", localDate(2025, 10, 17))
        insert("other-account", localDate(2025, 10, 18), accountId = OTHER_ACCOUNT_ID, feedId = OTHER_FEED_ID)

        val ids = database.articleDao().queryOnThisDay(ACCOUNT_ID, "10-18", "2026").map { it.article.id }

        assertEquals(listOf("last-year", "two-years"), ids)
    }

    @Test
    fun queryOnThisDay_ordersNewestFirstWithIdTieBreak() = runBlocking {
        insert("a", localDate(2025, 10, 18))
        insert("b", localDate(2025, 10, 18))
        insert("old", localDate(2020, 10, 18))

        val ids = database.articleDao().queryOnThisDay(ACCOUNT_ID, "10-18", "2026").map { it.article.id }

        assertEquals(listOf("b", "a", "old"), ids)
    }

    @Test
    fun queryOnThisDay_capsResultsAtLimit() = runBlocking {
        (2015..2025).forEach { year -> insert("y$year", localDate(year, 10, 18)) }

        val defaultLimit = database.articleDao().queryOnThisDay(ACCOUNT_ID, "10-18", "2026")
        val custom = database.articleDao().queryOnThisDay(ACCOUNT_ID, "10-18", "2026", limit = 2)

        assertEquals(DEFAULT_LIMIT, defaultLimit.size)
        assertEquals(listOf("y2025", "y2024", "y2023", "y2022", "y2021"), defaultLimit.map { it.article.id })
        assertEquals(2, custom.size)
    }

    @Test
    fun queryOnThisDay_leapDayMatchesOnlyLeapDay() = runBlocking {
        insert("leap-2024", localDate(2024, 2, 29))
        insert("leap-2020", localDate(2020, 2, 29))
        insert("feb28", localDate(2025, 2, 28))
        insert("mar1", localDate(2025, 3, 1))

        val dao = database.articleDao()

        assertEquals(listOf("leap-2024", "leap-2020"), dao.queryOnThisDay(ACCOUNT_ID, "02-29", "2028").map { it.article.id })
        assertEquals(listOf("feb28"), dao.queryOnThisDay(ACCOUNT_ID, "02-28", "2026").map { it.article.id })
        assertEquals(listOf("mar1"), dao.queryOnThisDay(ACCOUNT_ID, "03-01", "2026").map { it.article.id })
    }

    @Test
    fun queryOnThisDay_emptyWhenNothingMatches() = runBlocking {
        insert("other-day", localDate(2025, 5, 5))

        assertEquals(emptyList<String>(), database.articleDao().queryOnThisDay(ACCOUNT_ID, "10-18", "2026").map { it.article.id })
    }

    /** Buổi trưa theo múi giờ JVM mặc định để tránh sai lệch biên nửa đêm giữa JVM và SQLite localtime. */
    private fun localDate(year: Int, month: Int, day: Int): Date =
        Date.from(LocalDateTime.of(year, month, day, NOON_HOUR, 0).atZone(ZoneId.systemDefault()).toInstant())

    private suspend fun insert(
        id: String,
        date: Date,
        isUnread: Boolean = false,
        accountId: Int = ACCOUNT_ID,
        feedId: String = FEED_ID,
    ) {
        database.articleDao().insert(
            Article(
                id = id,
                date = date,
                title = id,
                rawDescription = "",
                shortDescription = "",
                link = "https://example.com/$id",
                feedId = feedId,
                accountId = accountId,
                isUnread = isUnread,
            )
        )
    }

    private companion object {
        const val ACCOUNT_ID = 7
        const val OTHER_ACCOUNT_ID = 8
        const val GROUP_ID = "group-1"
        const val OTHER_GROUP_ID = "group-2"
        const val FEED_ID = "feed-1"
        const val OTHER_FEED_ID = "feed-2"
        const val FEED_URL = "https://example.com/feed.xml"
        const val OTHER_FEED_URL = "https://example.org/feed.xml"
        const val DEFAULT_LIMIT = 5
        const val NOON_HOUR = 12
    }
}
