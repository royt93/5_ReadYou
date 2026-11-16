package com.mckimquyen.reader.domain.sv

import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.domain.repository.WidgetRefreshNotifier
import com.mckimquyen.reader.domain.watchdog.WatchdogEngine
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.articlehistory.ArticleHistoryRepository
import com.mckimquyen.reader.infrastructure.articlehistory.RetractionRadarService
import com.mckimquyen.reader.infrastructure.filter.SmartFilterManager
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedHttpClient
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedParser
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import com.mckimquyen.reader.infrastructure.watchdog.WatchdogManager
import kotlinx.coroutines.Dispatchers
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedWithArticle
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.page.home.feed.subs.NostrDeviceFixtures
import com.mckimquyen.reader.ui.page.home.feed.subs.NostrFakeRelay
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

/** Builds a LocalRssSv out of the running app's real DAOs and services, but with the two social clients we hand in. */
object LocalRssSvFactory {
    fun app(): RApp = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RApp

    fun smartFilterManager(app: RApp): SmartFilterManager =
        SmartFilterManager(app, app.applicationScope, app.ioDispatcher)

    fun build(blueskyApi: BlueskyApiClient, nostrRelay: NostrRelayClient): LocalRssSv {
        val app = app()
        val db = app.androidDatabase
        val dispatchers = app.ioDispatcher
        return LocalRssSv(
            context = app,
            articleDao = db.articleDao(),
            feedDao = db.feedDao(),
            rssHelper = app.rssHelper,
            notificationHelper = app.notificationHelper,
            accountDao = db.accountDao(),
            groupDao = db.groupDao(),
            feedHealthDao = db.feedHealthDao(),
            ioDispatcher = dispatchers,
            defaultDispatcher = Dispatchers.Default,
            workManager = app.workManager,
            watchdogManager = WatchdogManager(app, WatchdogEngine(), app.notificationHelper, app.applicationScope, dispatchers),
            smartFilterManager = smartFilterManager(app),
            webFeedScraper = WebFeedScraper(WebFeedHttpClient(), WebFeedParser()),
            smartCollectionMatcher = SmartCollectionMatcher(
                db.smartCollectionDao(), SemanticSearchEngine(db.articleEmbeddingDao()), db.articleDao(), Dispatchers.Default,
            ),
            widgetRefreshNotifier = WidgetRefreshNotifier { },
            retractionRadar = RetractionRadarService(app, db.articleRadarDao(), ArticleHistoryRepository(db.articleRadarDao())),
            nostrRelayClient = nostrRelay,
            blueskyApiClient = blueskyApi,
        )
    }
}

data class SyncResult(val outcome: FeedWithArticle?, val inserted: List<Article>)
data class TableCounts(val feed: Long, val article: Long, val feedHealth: Long, val group: Long)

class BackgroundSyncRig : AutoCloseable {
    private val app = LocalRssSvFactory.app()
    private val db = app.androidDatabase
    private val accountId = app.currentAccountId

    /** What the fake Bluesky API answers next; a test sets it before syncing. */
    @Volatile var profileResponse: () -> MockResponse = { json(BlueskyDeviceFixtures.PROFILE_BODY) }
    @Volatile var feedResponse: () -> MockResponse = { json(BlueskyDeviceFixtures.feedBody()) }

    private val blueskyServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.requestLine.contains("getProfile")) profileResponse() else feedResponse()
        }
        start()
    }
    val nostrRelay = NostrFakeRelay()

    val groupId = "front02h_probe_group"
    val blueskyFeed = Feed(
        id = "front02h_probe_bluesky", name = "Bluesky · Probe", icon = "https://cdn.bsky.app/img/avatar/probe@jpeg",
        url = "bsky:${BlueskyDeviceFixtures.HANDLE}", sourceUrl = "bsky:${BlueskyDeviceFixtures.HANDLE}",
        groupId = groupId, accountId = accountId, isNotification = false, sourceType = Feed.BLUESKY_SOURCE_TYPE,
    )
    val nostrFeed = Feed(
        id = "front02h_probe_nostr", name = "Nostr · Probe", icon = "https://example.invalid/probe.png",
        url = NostrFeedMapper.feedUrl(AUTHOR_NPUB), sourceUrl = NostrFeedMapper.feedUrl(AUTHOR_NPUB),
        groupId = groupId, accountId = accountId, isNotification = false, sourceType = Feed.NOSTR_SOURCE_TYPE,
    )

    val service: LocalRssSv = LocalRssSvFactory.build(
        BlueskyApiClient(app.okHttpClient, Dispatchers.IO, blueskyServer.url("/").toString().trimEnd('/'), SOCIAL_TIMEOUT_MS, SOCIAL_MAX_BODY_BYTES),
        NostrRelayClient(app.okHttpClient, Dispatchers.IO, listOf(nostrRelay.url), false, SOCIAL_TIMEOUT_MS),
    )
    private val filters = LocalRssSvFactory.smartFilterManager(app)

    suspend fun addProbeSources() {
        db.groupDao().insert(Group(id = groupId, name = "front02h probe", accountId = accountId))
        db.feedDao().insert(blueskyFeed, nostrFeed)
    }

    /** The three steps of one sync round for ONE feed; the same three calls AbstractRssRepository.sync makes. */
    suspend fun syncOnce(feed: Feed): SyncResult {
        val outcome = service.syncSingleFeed(feed)
        val inserted = if (outcome == null) emptyList() else db.articleDao().insertListIfNotExist(filters.applyRules(outcome.articles))
        return SyncResult(outcome, inserted)
    }

    fun feedDao() = db.feedDao()

    fun counts(): TableCounts = TableCounts(scalar("SELECT COUNT(*) FROM feed"), scalar("SELECT COUNT(*) FROM article"), scalar("SELECT COUNT(*) FROM feed_health_record"), scalar("SELECT COUNT(*) FROM \"group\""))

    /** Rows of the article table for one probe feed; the id is always a test constant, never outside data. */
    fun articleCountOf(feedId: String): Int = scalar("SELECT COUNT(*) FROM article WHERE feedId = '$feedId'").toInt()

    /** Any row anywhere whose id starts with the probe prefix; must be 0 after cleanUp. */
    fun probeRowCount(): Long = listOf(
        "SELECT COUNT(*) FROM feed WHERE id LIKE 'front02h_probe_%'",
        "SELECT COUNT(*) FROM \"group\" WHERE id LIKE 'front02h_probe_%'",
        "SELECT COUNT(*) FROM feed_health_record WHERE feedId LIKE 'front02h_probe_%'",
        "SELECT COUNT(*) FROM article WHERE feedId LIKE 'front02h_probe_%'",
    ).sumOf { scalar(it) }

    private fun scalar(sql: String): Long =
        db.openHelper.writableDatabase.query(sql).use { c ->
            c.moveToFirst()
            c.getLong(0)
        }

    /** Deleting the group cascades to both feeds and their articles (and the radar rows); the health rows have no foreign key. */
    suspend fun cleanUp() {
        db.feedHealthDao().deleteByFeedId(blueskyFeed.id)
        db.feedHealthDao().deleteByFeedId(nostrFeed.id)
        db.groupDao().queryById(groupId)?.let { db.groupDao().delete(it) }
    }

    override fun close() {
        blueskyServer.shutdown()
        nostrRelay.close()
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private companion object {
        /** A stored Nostr source is "nostr:<npub>" (bech32), never the hex key; the sync reads it back with NostrKeys.npubToHex. */
        val AUTHOR_NPUB: String = requireNotNull(NostrKeys.hexToNpub(NostrDeviceFixtures.AUTHOR_HEX))
        const val SOCIAL_TIMEOUT_MS = 5_000L
        const val SOCIAL_MAX_BODY_BYTES = 512 * 1024L
    }
}
