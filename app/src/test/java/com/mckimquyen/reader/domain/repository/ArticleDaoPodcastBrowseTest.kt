package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
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
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ArticleDaoPodcastBrowseTest {

    private lateinit var database: AndroidDatabase

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java
        ).allowMainThreadQueries().build()
        listOf(ACCOUNT to FEED, OTHER_ACCOUNT to OTHER_FEED).forEach { (account, feed) ->
            database.accountDao().insert(Account(id = account, name = "A$account", type = AccountType.Local))
            database.groupDao().insert(Group(id = "g$account", name = "G", accountId = account))
            database.feedDao().insert(Feed(id = feed, name = "Feed $feed", url = "https://e.com/$feed", groupId = "g$account", accountId = account))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun keepsOnlyUnreadAudioEpisodesOfTheAccount_newestFirst() = runBlocking {
        insert("old", 1, "Old")
        insert("new", 5, "New")
        insert("read", 4, "Read", unread = false)
        insert("noAudio", 3, "No audio", audio = null)
        insert("blankAudio", 2, "Blank audio", audio = "")
        insert("foreign", 9, "Foreign", account = OTHER_ACCOUNT, feed = OTHER_FEED)

        val items = database.articleDao().queryLatestUnreadPodcastEpisodes(ACCOUNT, limit = 10)

        assertEquals(listOf("new", "old"), items.map { it.id })
        assertEquals("Feed $FEED", items.first().feedName)
        assertEquals(DURATION, items.first().durationSec)
    }

    @Test
    fun respectsLimit() = runBlocking {
        (1..8).forEach { insert("e$it", it.toLong(), "T$it") }

        val items = database.articleDao().queryLatestUnreadPodcastEpisodes(ACCOUNT, limit = 5)

        assertEquals(listOf("e8", "e7", "e6", "e5", "e4"), items.map { it.id })
    }

    @Test
    fun sameDate_breaksTiesByIdDescending() = runBlocking {
        insert("a", 7, "A")
        insert("b", 7, "B")

        assertEquals(listOf("b", "a"), database.articleDao().queryLatestUnreadPodcastEpisodes(ACCOUNT, 5).map { it.id })
    }

    @Test
    fun noEpisodes_isAnEmptyListNotAnError() = runBlocking {
        assertTrue(database.articleDao().queryLatestUnreadPodcastEpisodes(ACCOUNT, 5).isEmpty())
    }

    @Test
    fun missingDuration_staysNull() = runBlocking {
        insert("n", 1, "N", duration = null)

        assertEquals(null, database.articleDao().queryLatestUnreadPodcastEpisodes(ACCOUNT, 5).single().durationSec)
    }

    @Test
    fun hugeBodiesDoNotBreakTheList() = runBlocking {
        insert("small", 2, "Small")
        insert("huge", 1, "Huge", raw = "x".repeat(2_000_000), full = "y".repeat(2_000_000))

        val items = database.articleDao().queryLatestUnreadPodcastEpisodes(ACCOUNT, 5)

        assertEquals(listOf("small", "huge"), items.map { it.id })
    }

    // Measured: this Robolectric setup reads a 2 MB body through a cursor without error, so the size alone cannot prove
    // the query leaves bodies alone. This guard reads the real query text instead (FIX-17).
    @Test
    fun querySelectsNoBodyOrUrlColumn() {
        val source = java.io.File("src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt").readText()
        val fn = source.indexOf("suspend fun queryLatestUnreadPodcastEpisodes")
        assertTrue("query function must exist", fn > 0)
        val queryStart = source.lastIndexOf("@Query(", fn)
        val selectClause = source.substring(queryStart, fn).substringAfter("SELECT").substringBefore("FROM")

        listOf("rawDescription", "fullContent", "shortDescription", "audioUrl", "aiSummary", "link", "img", "*").forEach {
            assertTrue("SELECT must not include $it: $selectClause", !selectClause.contains(it))
        }
        assertEquals(listOf("a.id AS id", "a.title AS title", "f.name AS feedName", "a.audioDurationSec AS durationSec"),
            selectClause.split(",").map { it.trim() })
    }

    private suspend fun insert(
        id: String,
        day: Long,
        title: String,
        unread: Boolean = true,
        audio: String? = "https://cdn.example.com/$id.mp3",
        duration: Int? = DURATION,
        account: Int = ACCOUNT,
        feed: String = FEED,
        raw: String = "",
        full: String? = null,
    ) {
        database.articleDao().insert(
            Article(
                id = id, date = Date(day * DAY_MS), title = title, rawDescription = raw, shortDescription = "",
                fullContent = full, link = "https://example.com/$id", feedId = feed, accountId = account,
                isUnread = unread, audioUrl = audio, audioMime = "audio/mpeg", audioDurationSec = duration,
            )
        )
    }

    private companion object {
        const val ACCOUNT = 7
        const val OTHER_ACCOUNT = 8
        const val FEED = "feed-7"
        const val OTHER_FEED = "feed-8"
        const val DAY_MS = 86_400_000L
        const val DURATION = 1_800
    }
}
