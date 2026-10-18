package com.mckimquyen.reader.domain.sv

import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
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
import com.mckimquyen.reader.ui.ext.spacerDollar
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
    smartCollectionMatcher: SmartCollectionMatcher? = null,
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
) {
    override suspend fun queryFeedArticles(feed: Feed, latestLink: String?): List<Article> {
        if (feed.sourceType == Feed.READ_LATER_SOURCE_TYPE || feed.id == Feed.READ_LATER_FEED_ID) {
            return emptyList()
        }
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
                    link = candidate.url.toString(),
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
}
