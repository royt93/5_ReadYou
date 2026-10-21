package com.mckimquyen.reader.infrastructure.rss

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflinePrecacheService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val rssHelper: RssHelper,
    private val imageLoader: ImageLoader,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val feedDao: com.mckimquyen.reader.domain.repository.FeedDao,
) {

    suspend fun precacheLatestUnread(accountId: Int, limit: Int = DEFAULT_LIMIT): Int =
        withContext(ioDispatcher) {
            if (!isOnWifi()) return@withContext 0

            val articles = articleDao.queryUnreadArticleList(accountId, limit)
            var cachedCount = 0

            for (article in articles) {
                val sourceType = feedDao.queryById(article.feedId)?.sourceType
                    ?: com.mckimquyen.reader.domain.model.feed.Feed.RSS_SOURCE_TYPE
                val storedContent = articleDao.queryArticleBody(accountId, article.id)?.fullContent
                val fullContent = storedContent
                    ?.takeIf { it.isNotBlank() }
                    ?: runCatching { rssHelper.parseFullContent(article.link, article.title, sourceType) }
                        .getOrNull()
                        .orEmpty()

                if (fullContent.isNotBlank() && fullContent != storedContent) {
                    articleDao.updateFullContent(accountId, article.id, fullContent)
                }

                // ponytail: WEB image prefetch stays off until each image URL uses validated/pinned DNS.
                val urls = if (sourceType == com.mckimquyen.reader.domain.model.feed.Feed.WEB_SOURCE_TYPE) emptyList() else buildList {
                    article.img?.takeIf { it.isNotBlank() }?.let(::add)
                    addAll(rssHelper.extractImageUrls(fullContent))
                }.distinct().take(MAX_IMAGES_PER_ARTICLE)

                urls.forEach { url ->
                    imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build()
                    )
                }
                if (fullContent.isNotBlank()) cachedCount++
            }

            cachedCount
        }

    fun isOnWifi(): Boolean {
        val manager = context.getSystemService<ConnectivityManager>() ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_IMAGES_PER_ARTICLE = 20
    }
}
