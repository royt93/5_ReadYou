package com.mckimquyen.reader.domain.sv

import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.domain.repository.WidgetRefreshNotifier
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.watchdog.WatchdogManager
import javax.inject.Inject
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.infrastructure.rss.RssDescriptionParser
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedRefreshResult
import com.mckimquyen.reader.domain.security.UrlTrackingCleaner
import com.mckimquyen.reader.ui.ext.spacerDollar
import com.mckimquyen.reader.infrastructure.articlehistory.RetractionRadarService
import com.mckimquyen.reader.ui.ext.urlTrackingCleanEnabled
import java.util.Date
import java.util.UUID

class LocalRssSv @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val rssHelper: RssHelper,
    private val notificationHelper: NotificationHelper,
    private val accountDao: AccountDao,
    private val groupDao: GroupDao,
    private val feedHealthDao: com.mckimquyen.reader.domain.repository.FeedHealthDao,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
    workManager: WorkManager,
    watchdogManager: WatchdogManager,
    smartFilterManager: com.mckimquyen.reader.infrastructure.filter.SmartFilterManager,
    private val webFeedScraper: WebFeedScraper,
    smartCollectionMatcher: SmartCollectionMatcher,
    widgetRefreshNotifier: WidgetRefreshNotifier,
    retractionRadar: RetractionRadarService,
    private val nostrRelayClient: com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient,
    private val blueskyApiClient: com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient,
) : AbstractRssRepository(
    context,
    accountDao,
    articleDao,
    groupDao,
    feedDao,
    workManager,
    rssHelper,
    notificationHelper,
    ioDispatcher,
    defaultDispatcher,
    watchdogManager,
    feedHealthDao,
    smartFilterManager,
    smartCollectionMatcher,
    widgetRefreshNotifier,
    retractionRadar,
) {
    override val scansFullFeedForRadar: Boolean = true

    override suspend fun queryFeedArticles(feed: Feed, latestLink: String?): List<Article> {
        if (feed.sourceType == Feed.READ_LATER_SOURCE_TYPE || feed.id == Feed.READ_LATER_FEED_ID) {
            return emptyList()
        }
        if (feed.sourceType == Feed.NOSTR_SOURCE_TYPE) return queryNostrArticles(feed)
        if (feed.sourceType == Feed.BLUESKY_SOURCE_TYPE) return queryBlueskyArticles(feed)
        if (feed.sourceType != Feed.WEB_SOURCE_TYPE) return super.queryFeedArticles(feed, latestLink)
        return when (val result = webFeedScraper.refresh(feed.sourceUrl ?: feed.url)) {
            is WebFeedRefreshResult.Success -> result.articles.mapNotNull { candidate ->
                if (candidate.title.isBlank() || candidate.url.scheme != "https") return@mapNotNull null
                Article(
                    id = feed.accountId.spacerDollar(UUID.randomUUID().toString()),
                    date = candidate.publishedAt ?: Date(),
                    title = candidate.title,
                    rawDescription = "",
                    shortDescription = candidate.title.take(RssDescriptionParser.MAX_SHORT_DESCRIPTION_LENGTH),
                    link = if (context.urlTrackingCleanEnabled) {
                        UrlTrackingCleaner.clean(candidate.url.toString())
                    } else {
                        candidate.url.toString()
                    },
                    feedId = feed.id,
                    accountId = feed.accountId,
                )
            }
            is WebFeedRefreshResult.Failure -> when (result.reason) {
                com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedDiscoveryResult.Timeout ->
                    throw java.net.SocketTimeoutException("Web feed refresh timed out")
                is com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedDiscoveryResult.HttpError ->
                    throw java.io.IOException("HTTP ${result.reason.statusCode}")
                else -> throw java.io.IOException("Web feed refresh failed: ${result.reason}")
            }
        }
    }
    private suspend fun queryNostrArticles(feed: Feed): List<Article> {
        val npub = (feed.sourceUrl ?: feed.url).removePrefix(
            com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper.FEED_URL_PREFIX,
        )
        val authorHex = com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys.npubToHex(npub)
            ?: throw java.io.IOException("Nostr feed address is not a public key")
        // The profile name is NOT used here: a background sync must never rename a source the user may have renamed.
        val events = nostrRelayClient.fetch(authorHex).notes
        if (events.isEmpty()) throw java.io.IOException("Nostr relays returned no notes")
        return com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper.toArticles(
            events = events,
            feedId = feed.id,
            accountId = feed.accountId,
            nowMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun queryBlueskyArticles(feed: Feed): List<Article> {
        val actor = (feed.sourceUrl ?: feed.url).removePrefix(
            com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.FEED_URL_PREFIX,
        )
        // Neither the display name nor the avatar is used here: a background sync must never rename a source the user may have renamed.
        val found = blueskyApiClient.fetch(actor)
            as? com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyOutcome.Found
            ?: throw java.io.IOException("Bluesky API returned no account")
        val articles = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.toArticles(
            profileDid = found.result.did,
            items = found.result.items,
            feedId = feed.id,
            accountId = feed.accountId,
            nowMillis = System.currentTimeMillis(),
        )
        if (articles.isEmpty()) throw java.io.IOException("Bluesky API returned no posts")
        return articles
    }
}
