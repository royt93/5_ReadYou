package com.mckimquyen.reader.domain.sv

import android.content.Context
import android.text.Html
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.sec.GoogleReaderSecurityKey
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.FeedHealthDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.infrastructure.di.MainDispatcher
import com.mckimquyen.reader.infrastructure.filter.SmartFilterManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.provider.googleReader.GoogleReaderApi
import com.mckimquyen.reader.infrastructure.watchdog.WatchdogManager
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.dollarLast
import com.mckimquyen.reader.ui.ext.getDefaultGroupId
import com.mckimquyen.reader.ui.ext.showToast
import com.mckimquyen.reader.ui.ext.spacerDollar
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

/**
 * RSS backend for self-hosted servers speaking the "Google Reader API" v1 protocol:
 * FreshRSS, Miniflux (compat mode), Nextcloud News, BazQux, etc.
 */
class GoogleReaderRssSv @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val rssHelper: RssHelper,
    private val notificationHelper: NotificationHelper,
    private val accountDao: AccountDao,
    private val groupDao: GroupDao,
    private val feedHealthDao: FeedHealthDao,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher
    private val mainDispatcher: CoroutineDispatcher,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
    workManager: WorkManager,
    watchdogManager: WatchdogManager,
    smartFilterManager: SmartFilterManager,
) : AbstractRssRepository(
    context, accountDao, articleDao, groupDao,
    feedDao, workManager, rssHelper, notificationHelper, ioDispatcher, defaultDispatcher,
    watchdogManager, feedHealthDao, smartFilterManager,
) {

    override val subscribe: Boolean = false
    override val move: Boolean = false
    override val delete: Boolean = false
    override val update: Boolean = false

    private suspend fun getGoogleReaderApi(): GoogleReaderApi {
        val account = accountDao.queryById(context.currentAccountId)
            ?: throw IllegalStateException("Account not found")
        val securityKey = GoogleReaderSecurityKey(account.securityKey)
        val serverUrl = securityKey.serverUrl
            ?: throw IllegalStateException("Server URL not configured")
        val username = securityKey.username
            ?: throw IllegalStateException("Username not configured")
        val password = securityKey.password
            ?: throw IllegalStateException("Password not configured")
        return GoogleReaderApi.getInstance(serverUrl = serverUrl, username = username, password = password)
    }

    override suspend fun validCredentials(): Boolean = getGoogleReaderApi().validCredentials()

    override suspend fun subscribe(feed: Feed, articles: List<Article>) {
        throw Exception("Unsupported")
    }

    override suspend fun addGroup(name: String): String {
        throw Exception("Unsupported")
    }

    /**
     * 1. Fetch categories (subscription/list) -> Groups
     * 2. Fetch feeds (subscription/list) mapped to their first category or the account default group
     * 3. Fetch stream/contents/reading-list, paginated up to [MAX_PAGES], mapped to Articles
     *    (read/starred state derived from item categories, matching Google Reader tag convention)
     */
    override suspend fun sync(coroutineWorker: CoroutineWorker): ListenableWorker.Result = supervisorScope {
        coroutineWorker.setProgress(SyncWorker.setIsSyncing(true))
        try {
            val accountId = context.currentAccountId
            val account = accountDao.queryById(accountId)
                ?: throw IllegalStateException("Account not found")
            val api = getGoogleReaderApi()
            val defaultGroupId = accountId.getDefaultGroupId()

            // 1 & 2. Subscriptions -> groups + feeds
            val subscriptions = api.getSubscriptionList().subscriptions.orEmpty()

            val groups = subscriptions
                .flatMap { it.categories.orEmpty() }
                .mapNotNull { it.id }
                .distinct()
                .mapNotNull { categoryId ->
                    val label = subscriptions
                        .flatMap { it.categories.orEmpty() }
                        .firstOrNull { it.id == categoryId }?.label
                    if (label == null) null else Group(
                        id = accountId.spacerDollar(categoryId),
                        name = label,
                        accountId = accountId,
                    )
                }
            groupDao.insertOrUpdate(groups)

            val feedRemoteIdToLocalId = mutableMapOf<String, String>()
            val feeds = subscriptions.mapNotNull { sub ->
                val remoteFeedId = sub.id
                val url = sub.url
                if (remoteFeedId == null || url == null) {
                    Log.w(TAG, "Skip incomplete Google Reader feed id=$remoteFeedId hasUrl=${url != null}")
                    null
                } else {
                    val categoryId = sub.categories.orEmpty().firstOrNull()?.id
                    val groupId = if (categoryId != null) accountId.spacerDollar(categoryId) else defaultGroupId
                    val localId = accountId.spacerDollar(remoteFeedId)
                    feedRemoteIdToLocalId[remoteFeedId] = localId
                    Feed(
                        id = localId,
                        name = sub.title ?: context.getString(R.string.empty),
                        url = url,
                        groupId = groupId,
                        accountId = accountId,
                        icon = sub.iconUrl,
                    )
                }
            }
            feedDao.insertOrUpdate(feeds)

            // 3. Reading list, paginated
            var continuation: String? = null
            var page = 0
            while (page < MAX_PAGES) {
                val body = api.getStreamContents(continuation = continuation)
                val items = body.items.orEmpty()
                if (items.isEmpty()) break

                val articles = items.mapNotNull { item ->
                    val remoteItemId = item.id
                    val remoteFeedId = item.origin?.firstOrNull()?.streamId
                    val localFeedId = remoteFeedId?.let { feedRemoteIdToLocalId[it] }
                    if (remoteItemId == null || localFeedId == null) {
                        Log.w(TAG, "Skip incomplete Google Reader item id=$remoteItemId feedStream=$remoteFeedId")
                        null
                    } else {
                        val categories = item.categories.orEmpty()
                        val rawHtml = item.summary?.content ?: ""
                        Article(
                            id = accountId.spacerDollar(remoteItemId),
                            date = item.published?.let { Date(it * 1000) } ?: Date(),
                            title = item.title?.let { decodeHtmlEntities(it) } ?: context.getString(R.string.empty),
                            author = item.author,
                            rawDescription = rawHtml,
                            shortDescription = Html.fromHtml(rawHtml, Html.FROM_HTML_MODE_LEGACY).toString()
                                .take(SHORT_DESCRIPTION_LENGTH)
                                .trim(),
                            fullContent = rawHtml,
                            img = rssHelper.findImg(rawHtml),
                            link = item.summary?.canonical?.firstOrNull()?.href
                                ?: item.summary?.alternate?.firstOrNull()?.href
                                ?: "",
                            feedId = localFeedId,
                            accountId = accountId,
                            isUnread = !categories.contains(GoogleReaderApi.TAG_READ),
                            isStarred = categories.contains(GoogleReaderApi.TAG_STARRED),
                            updateAt = Date(),
                        )
                    }
                }
                if (articles.isNotEmpty()) {
                    articleDao.insertOnConflictIgnore(*articles.toTypedArray())
                }

                continuation = body.continuation
                page++
                if (continuation.isNullOrBlank()) break
            }

            accountDao.update(account.apply { updateAt = Date() })
            ListenableWorker.Result.success(SyncWorker.setIsSyncing(false))
        } catch (e: Exception) {
            Log.e(TAG, "On sync exception: ${e.message}", e)
            withContext(mainDispatcher) {
                context.showToast(e.message)
            }
            ListenableWorker.Result.failure(SyncWorker.setIsSyncing(false))
        }
    }

    override suspend fun markAsRead(
        groupId: String?,
        feedId: String?,
        articleId: String?,
        before: Date?,
        isUnread: Boolean,
    ) {
        super.markAsRead(groupId, feedId, articleId, before, isUnread)
        if (articleId == null) return // Bulk mark-all is applied locally only; server bulk tagging is out of scope here.
        val api = getGoogleReaderApi()
        api.editTag(
            itemId = articleId.dollarLast(),
            tag = GoogleReaderApi.TAG_READ,
            add = !isUnread,
        )
    }

    override suspend fun markAsStarred(articleId: String, isStarred: Boolean) {
        super.markAsStarred(articleId, isStarred)
        val api = getGoogleReaderApi()
        api.editTag(
            itemId = articleId.dollarLast(),
            tag = GoogleReaderApi.TAG_STARRED,
            add = isStarred,
        )
    }

    private fun decodeHtmlEntities(text: String): String =
        Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()

    private companion object {
        const val TAG = "GoogleReaderRssSv"
        const val MAX_PAGES = 10
        const val SHORT_DESCRIPTION_LENGTH = 110
    }
}
