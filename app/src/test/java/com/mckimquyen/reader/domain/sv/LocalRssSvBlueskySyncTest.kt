package com.mckimquyen.reader.domain.sv

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedHealthDao
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFetchResult
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyOutcome
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalRssSvBlueskySyncTest {
    private val api = mockk<BlueskyApiClient>()
    private val rss = mockk<RssHelper>(relaxed = true)
    private val filters = mockk<com.mckimquyen.reader.infrastructure.filter.SmartFilterManager> {
        io.mockk.every { applyRules(any()) } answers { firstArg() }
    }
    private val service = LocalRssSv(
        context = ApplicationProvider.getApplicationContext(), articleDao = mockk<ArticleDao>(relaxed = true),
        feedDao = mockk(relaxed = true), rssHelper = rss, notificationHelper = mockk(relaxed = true),
        accountDao = mockk(relaxed = true), groupDao = mockk(relaxed = true), feedHealthDao = mockk<FeedHealthDao>(relaxed = true),
        ioDispatcher = Dispatchers.IO, defaultDispatcher = Dispatchers.Default,
        workManager = mockk(relaxed = true), watchdogManager = mockk(relaxed = true),
        smartFilterManager = filters, webFeedScraper = mockk<WebFeedScraper>(),
        smartCollectionMatcher = mockk(relaxed = true), widgetRefreshNotifier = mockk(relaxed = true),
        retractionRadar = mockk(relaxed = true), nostrRelayClient = mockk(relaxed = true), blueskyApiClient = api,
    )
    private val feed = Feed(
        id = "bsky-feed", name = "Bluesky · Alice", url = "bsky:alice.bsky.social", groupId = "group", accountId = 1,
        icon = "https://cdn.bsky.app/img/avatar/a@jpeg", sourceType = Feed.BLUESKY_SOURCE_TYPE, sourceUrl = "bsky:alice.bsky.social",
    )

    private fun found(vararg items: String, displayName: String? = "Someone New") = BlueskyOutcome.Found(
        BlueskyFetchResult(BlueskyTestPayloads.array(*items), BlueskyTestPayloads.DID, BlueskyTestPayloads.HANDLE, displayName, "https://cdn.bsky.app/x"),
    )

    @Test fun theSourceTypeConstant_isBluesky() = assertEquals("BLUESKY", Feed.BLUESKY_SOURCE_TYPE)

    @Test fun aSync_readsTheOwnPostsOfTheAccount_throughTheApiClient() = runBlocking {
        coEvery { api.fetch("alice.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "one"), BlueskyTestPayloads.post("3k2", "two"))
        val articles = service.syncSingleFeed(feed)?.articles.orEmpty()
        assertEquals(setOf("one", "two"), articles.map { it.title }.toSet())
        assertTrue(articles.all { it.feedId == "bsky-feed" && it.link.startsWith("https://bsky.app/profile/") })
        coVerify(exactly = 1) { api.fetch("alice.bsky.social") }
    }

    // FRONT-02p: the address menu now writes the typed address to both columns, and the sync follows it.
    @Test fun aSourceWhoseTwoAddressesWereEdited_isSyncedFromTheNewOne() = runBlocking {
        coEvery { api.fetch("bob.bsky.social") } returns found(BlueskyTestPayloads.post("3k1", "one"))
        service.syncSingleFeed(feed.copy(url = "bsky:bob.bsky.social", sourceUrl = "bsky:bob.bsky.social"))
        coVerify(exactly = 1) { api.fetch("bob.bsky.social") }
        coVerify(exactly = 0) { api.fetch("alice.bsky.social") }
    }

    @Test fun aSync_neverCallsTheRssHelper_orFavicons() = runBlocking {
        coEvery { api.fetch(any()) } returns found(BlueskyTestPayloads.post("3k1", "one"))
        service.syncSingleFeed(feed)
        coVerify(exactly = 0) { rss.queryRssXml(any(), any()) }
        coVerify(exactly = 0) { rss.queryRssIcon(any(), any()) }
    }

    @Test fun aSync_neverRenamesTheFeed_nor_changesItsIcon() = runBlocking {
        coEvery { api.fetch(any()) } returns found(BlueskyTestPayloads.post("3k1", "one"), displayName = "Totally New Name")
        val result = service.syncSingleFeed(feed)
        assertEquals("Bluesky · Alice", result?.feed?.name)
        assertEquals("https://cdn.bsky.app/img/avatar/a@jpeg", result?.feed?.icon)
    }

    @Test fun aFeedStoredByDid_isReadByDid() = runBlocking {
        val byDid = feed.copy(url = "bsky:${BlueskyTestPayloads.DID}", sourceUrl = "bsky:${BlueskyTestPayloads.DID}")
        coEvery { api.fetch(BlueskyTestPayloads.DID) } returns found(BlueskyTestPayloads.post("3k1", "one"))
        assertEquals(1, service.syncSingleFeed(byDid)?.articles?.size)
    }

    @Test fun failed_andNotFound_areBothSyncFailures_notEmptyFeeds() = runBlocking {
        coEvery { api.fetch(any()) } returns BlueskyOutcome.Failed
        assertNull(service.syncSingleFeed(feed))
        coEvery { api.fetch(any()) } returns BlueskyOutcome.NotFound
        assertNull(service.syncSingleFeed(feed))
    }

    @Test fun anAnswerWithNoUsablePosts_isASyncFailure() = runBlocking {
        coEvery { api.fetch(any()) } returns found(BlueskyTestPayloads.repost("r1"))
        assertNull(service.syncSingleFeed(feed))
    }

    // The client itself refuses such an actor without sending anything (BlueskyApiClientTest); here the sync must treat that as a failure.
    @Test fun aStoredUrlThatIsNotAnActor_isASyncFailure_notAnEmptyFeed() = runBlocking {
        val broken = feed.copy(url = "bsky:not an actor", sourceUrl = "bsky:not an actor")
        coEvery { api.fetch("not an actor") } returns BlueskyOutcome.Failed
        assertNull(service.syncSingleFeed(broken))
        coVerify(exactly = 1) { api.fetch("not an actor") }
    }

    @Test fun anRssFeed_stillUsesTheRssHelper_andNeverTheApi() = runBlocking {
        coEvery { rss.queryRssXml(any(), any()) } returns emptyList()
        service.syncSingleFeed(feed.copy(sourceType = Feed.RSS_SOURCE_TYPE, url = "https://bsky.app/profile/x/rss", sourceUrl = null))
        coVerify(exactly = 1) { rss.queryRssXml(any(), any()) }
        coVerify(exactly = 0) { api.fetch(any()) }
    }
}
