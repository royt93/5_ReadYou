package com.mckimquyen.reader.domain.sv

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedHealthDao
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFetchResult
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import com.mckimquyen.reader.infrastructure.social.nostr.NostrTestEvents
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalRssSvNostrSyncTest {
    private val npub = requireNotNull(NostrKeys.hexToNpub(NostrTestEvents.AUTHOR))
    private val relay = mockk<NostrRelayClient>()
    private val rss = mockk<RssHelper>(relaxed = true)
    private val scraper = mockk<WebFeedScraper>()
    private val filters = mockk<com.mckimquyen.reader.infrastructure.filter.SmartFilterManager> {
        io.mockk.every { applyRules(any()) } answers { firstArg() }
    }
    private val service = LocalRssSv(
        context = ApplicationProvider.getApplicationContext(), articleDao = mockk<ArticleDao>(relaxed = true),
        feedDao = mockk(relaxed = true), rssHelper = rss, notificationHelper = mockk(relaxed = true),
        accountDao = mockk(relaxed = true), groupDao = mockk(relaxed = true), feedHealthDao = mockk<FeedHealthDao>(relaxed = true),
        ioDispatcher = Dispatchers.IO, defaultDispatcher = Dispatchers.Default,
        workManager = mockk(relaxed = true), watchdogManager = mockk(relaxed = true),
        smartFilterManager = filters, webFeedScraper = scraper,
        smartCollectionMatcher = mockk(relaxed = true), widgetRefreshNotifier = mockk(relaxed = true),
        retractionRadar = mockk(relaxed = true), nostrRelayClient = relay,
    )
    private val feed = Feed(
        id = "nostr-feed", name = "Nostr", url = "nostr:$npub", groupId = "group", accountId = 1,
        icon = "icon", sourceType = Feed.NOSTR_SOURCE_TYPE, sourceUrl = "nostr:$npub",
    )

    private fun notes(vararg json: String): List<NostrEvent> = json.map { requireNotNull(NostrEvent.parse(it)) }

    @Test fun theSourceTypeConstant_isNostr() = assertEquals("NOSTR", Feed.NOSTR_SOURCE_TYPE)

    @Test fun queryFeedArticles_readsTheNotesOfTheNpub_throughTheRelayClient() = runBlocking {
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(notes(NostrTestEvents.PLAIN, NostrTestEvents.OLDER), null)
        val articles = service.syncSingleFeed(feed)?.articles.orEmpty()
        assertEquals(listOf("Hello nostr, first note", "Older note"), articles.map { it.title })
        assertTrue(articles.all { it.feedId == "nostr-feed" && it.link.startsWith("https://njump.me/note1") })
        coVerify(exactly = 1) { relay.fetch(NostrTestEvents.AUTHOR) }
    }

    @Test fun aNoteDatedInTheFuture_isClampedToTheTimeOfTheSync() = runBlocking {
        coEvery { relay.fetch(NostrTestEvents.AUTHOR) } returns NostrFetchResult(notes(NostrTestEvents.FUTURE), null)
        val before = System.currentTimeMillis()
        val article = service.syncSingleFeed(feed)?.articles.orEmpty().single()
        val after = System.currentTimeMillis()
        assertTrue("date=${article.date.time}", article.date.time in before..after)
    }

    @Test fun queryFeedArticles_neverCallsTheRssHelperOrTheWebScraper() = runBlocking {
        coEvery { relay.fetch(any()) } returns NostrFetchResult(notes(NostrTestEvents.PLAIN), null)
        service.syncSingleFeed(feed)
        coVerify(exactly = 0) { rss.queryRssXml(any(), any()) }
        coVerify(exactly = 0) { rss.queryRssIcon(any(), any()) }
    }

    @Test fun anEmptyRelayAnswer_isASyncFailure_notAnEmptyFeed() = runBlocking {
        coEvery { relay.fetch(any()) } returns NostrFetchResult.EMPTY
        assertEquals(null, service.syncSingleFeed(feed))
    }

    @Test fun aStoredUrlThatIsNotAnNpub_isAFailure_andNeverReachesTheRelay() = runBlocking {
        val broken = feed.copy(url = "nostr:not-an-npub", sourceUrl = "nostr:not-an-npub")
        assertEquals(null, service.syncSingleFeed(broken))
        coVerify(exactly = 0) { relay.fetch(any()) }
    }

    @Test fun anRssFeed_stillUsesTheRssHelper() = runBlocking {
        coEvery { rss.queryRssXml(any(), any()) } returns emptyList()
        service.syncSingleFeed(feed.copy(sourceType = Feed.RSS_SOURCE_TYPE, url = "https://example.test/feed"))
        coVerify(exactly = 1) { rss.queryRssXml(any(), any()) }
        coVerify(exactly = 0) { relay.fetch(any()) }
    }
}
