package com.mckimquyen.reader.domain.sv

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.infrastructure.filter.SmartFilterManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Subscribing writes a source and its first articles. They must succeed or fail TOGETHER: a source that is saved without any article
 * (because the article write failed) would show an empty source the user never asked for. Real Room, in memory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalRssSvSubscribeAtomicTest {
    private lateinit var database: AndroidDatabase
    private lateinit var service: LocalRssSv
    private val accountId = 1
    private val group = Group(id = "group", name = "News", accountId = accountId)
    private val feed = Feed(id = "feed-1", name = "Probe", url = "nostr:npub1probe", groupId = group.id, accountId = accountId)

    private fun article(id: String, link: String = "https://a.example/$id") = Article(
        id = id, date = Date(1L), title = "t $id", rawDescription = "RAW", shortDescription = "SHORT", link = link,
        feedId = feed.id, accountId = accountId,
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        val filters = mockk<SmartFilterManager> { every { applyRules(any()) } answers { firstArg() } }
        service = LocalRssSv(
            context = ApplicationProvider.getApplicationContext(), articleDao = database.articleDao(),
            feedDao = database.feedDao(), rssHelper = mockk(relaxed = true), notificationHelper = mockk(relaxed = true),
            accountDao = database.accountDao(), groupDao = database.groupDao(), feedHealthDao = database.feedHealthDao(),
            ioDispatcher = Dispatchers.IO, defaultDispatcher = Dispatchers.Default,
            workManager = mockk(relaxed = true), watchdogManager = mockk(relaxed = true), smartFilterManager = filters,
            webFeedScraper = mockk(relaxed = true), smartCollectionMatcher = mockk(relaxed = true),
            widgetRefreshNotifier = mockk(relaxed = true), retractionRadar = mockk(relaxed = true),
            nostrRelayClient = mockk(relaxed = true), blueskyApiClient = mockk(relaxed = true),
        )
        runBlocking {
            database.accountDao().insert(Account(id = accountId, name = "Local", type = AccountType.Local))
            database.groupDao().insert(group)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun storedFeeds() = database.feedDao().queryAll(accountId)
    private suspend fun storedArticleCount() = database.articleDao().queryUnreadArticleList(accountId, 50).size

    @Test
    fun subscribe_savesTheSourceAndAllItsArticles() = runBlocking {
        service.subscribe(feed, listOf(article("a1"), article("a2"), article("a3")))
        assertEquals(listOf(feed.id), storedFeeds().map { it.id })
        assertEquals(3, storedArticleCount())
    }

    @Test
    fun subscribe_withNoArticles_stillSavesTheSource() = runBlocking {
        service.subscribe(feed, emptyList())
        assertEquals(listOf(feed.id), storedFeeds().map { it.id })
        assertEquals(0, storedArticleCount())
    }

    @Test
    fun subscribe_whenTheArticlesCannotBeWritten_leavesNoSourceBehind() = runBlocking {
        // Two articles with the same primary key: the article write fails after the source row was already written.
        val failed = runCatching { service.subscribe(feed, listOf(article("same"), article("same", link = "https://a.example/other"))) }
        assertTrue("the failure must reach the caller", failed.isFailure)
        assertEquals("a source without its articles must not stay behind", emptyList<String>(), storedFeeds().map { it.id })
        assertEquals(0, storedArticleCount())
    }

    @Test
    fun subscribe_whenTheArticlesCannotBeWritten_doesNotDisturbAnEarlierSource() = runBlocking {
        val earlier = feed.copy(id = "feed-0", name = "Earlier", url = "nostr:npub1earlier")
        service.subscribe(earlier, listOf(article("e1").copy(feedId = earlier.id)))
        runCatching { service.subscribe(feed, listOf(article("same"), article("same", link = "https://a.example/other"))) }
        assertEquals(listOf(earlier.id), storedFeeds().map { it.id })
        assertEquals(1, storedArticleCount())
    }

    @Test
    fun subscribe_pointsEveryArticleAtTheSavedSource_evenWhenItCameWithAnotherFeedId() = runBlocking {
        service.subscribe(feed, listOf(article("a1").copy(feedId = "generated-by-mapper"), article("a2")))
        assertEquals(2, storedArticleCount())
        assertEquals(setOf(feed.id), database.articleDao().queryUnreadArticleList(accountId, 50).map { it.feedId }.toSet())
    }

    @Test
    fun subscribe_ofAnAlreadyStoredSource_replacesItAndKeepsTheNewArticles() = runBlocking {
        service.subscribe(feed, listOf(article("a1")))
        service.subscribe(feed.copy(name = "Renamed"), listOf(article("b1", link = "https://a.example/b1")))
        assertEquals(listOf("Renamed"), storedFeeds().map { it.name })
    }
}
