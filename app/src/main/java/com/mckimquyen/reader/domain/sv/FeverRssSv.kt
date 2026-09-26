package com.mckimquyen.reader.domain.sv

import android.content.Context
import android.text.Html
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.sec.FeverSecurityKey
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleMeta
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.infrastructure.di.MainDispatcher
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.rss.provider.fever.FeverAPI
import com.mckimquyen.reader.infrastructure.rss.provider.fever.FeverDTO
import com.mckimquyen.reader.infrastructure.watchdog.WatchdogManager
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.dollarLast
import com.mckimquyen.reader.ui.ext.showToast
import com.mckimquyen.reader.ui.ext.spacerDollar
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import java.util.Date
import javax.inject.Inject

class FeverRssSv @Inject constructor(
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
    @MainDispatcher
    private val mainDispatcher: CoroutineDispatcher,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
    workManager: WorkManager,
    watchdogManager: WatchdogManager,
    smartFilterManager: com.mckimquyen.reader.infrastructure.filter.SmartFilterManager,
) : AbstractRssRepository(
    context, accountDao, articleDao, groupDao,
    feedDao, workManager, rssHelper, notificationHelper, ioDispatcher, defaultDispatcher,
    watchdogManager, feedHealthDao, smartFilterManager
) {

    override val subscribe: Boolean = false
    override val move: Boolean = false
    override val delete: Boolean = false
    override val update: Boolean = false

    private suspend fun getFeverAPI(): FeverAPI {
        val account = accountDao.queryById(context.currentAccountId)
            ?: throw IllegalStateException("Account not found")
        val securityKey = FeverSecurityKey(account.securityKey)
        val serverUrl = securityKey.serverUrl
            ?: throw IllegalStateException("Fever server URL not configured")
        val username = securityKey.username
            ?: throw IllegalStateException("Fever username not configured")
        val password = securityKey.password
            ?: throw IllegalStateException("Fever password not configured")
        return FeverAPI.getInstance(
            serverUrl = serverUrl,
            username = username,
            password = password,
            httpUsername = null,
            httpPassword = null,
        )
    }

    override suspend fun validCredentials(): Boolean = getFeverAPI().validCredentials() > 0

    override suspend fun subscribe(feed: Feed, articles: List<Article>) {
        throw Exception("Unsupported")
    }

    override suspend fun addGroup(name: String): String {
        throw Exception("Unsupported")
    }

    /**
     * Fever API synchronous processing with object's ID to ensure idempotence
     * and handle foreign key relationships such as read status, starred status, etc.
     *
     * When synchronizing articles, 50 articles will be pulled in each round.
     * The ID of the 50th article in this round will be recorded and
     * used as the starting mark for the next pull until the number of articles
     * obtained is 0 or their quantity exceeds 250, at which point the pulling process stops.
     *
     * 1. Fetch the Fever groups
     * 2. Fetch the Fever feeds (including favicons)
     * 3. Fetch the Fever articles
     * 4. Synchronize read/unread and starred/un-starred items
     */
    override suspend fun sync(coroutineWorker: CoroutineWorker): ListenableWorker.Result = supervisorScope {
        coroutineWorker.setProgress(SyncWorker.setIsSyncing(true))

        try {
            val preTime = System.currentTimeMillis()
            val accountId = context.currentAccountId
            val account = accountDao.queryById(accountId)
                ?: throw IllegalStateException("Account not found")
            val feverAPI = getFeverAPI()

            // 1. Fetch the Fever groups
            groupDao.insertOrUpdate(
                feverAPI.getGroups().groups.orEmpty().mapNotNull { group ->
                    val remoteGroupId = group.id
                    if (remoteGroupId == null) {
                        Log.w(TAG, "Skip Fever group without id")
                        null
                    } else {
                        Group(
                            id = accountId.spacerDollar(remoteGroupId),
                            name = group.title ?: context.getString(R.string.empty),
                            accountId = accountId,
                        )
                    }
                }
            )

            // 2. Fetch the Fever feeds
            val feedsBody = feverAPI.getFeeds()
            val feedsGroupsMap = mutableMapOf<String, String>()
            feedsBody.feeds_groups?.forEach { feedsGroups ->
                feedsGroups.group_id?.toString()?.let { groupId ->
                    feedsGroups.feed_ids?.split(",")?.forEach { feedId ->
                        feedsGroupsMap[feedId] = groupId
                    }
                }
            }

            // Fetch the Fever favicons
            val faviconsById = feverAPI.getFavicons().favicons?.associateBy { it.id } ?: emptyMap()
            val feeds = feedsBody.feeds.orEmpty().mapNotNull { feed ->
                val remoteFeedId = feed.id
                val url = feed.url
                val remoteGroupId = remoteFeedId?.let { feedsGroupsMap[it.toString()] }
                if (remoteFeedId == null || url == null || remoteGroupId == null) {
                    // A feed without id/url/group cannot satisfy the Feed -> Group foreign key.
                    Log.w(TAG, "Skip incomplete Fever feed id=$remoteFeedId hasUrl=${url != null} group=$remoteGroupId")
                    null
                } else {
                    Feed(
                        id = accountId.spacerDollar(remoteFeedId),
                        name = feed.title ?: context.getString(R.string.empty),
                        url = url,
                        groupId = accountId.spacerDollar(remoteGroupId),
                        accountId = accountId,
                        icon = faviconsById[feed.favicon_id]?.data
                    )
                }
            }
            feedDao.insertOrUpdate(feeds)

            // 3. Fetch the Fever articles (up to unlimited counts)
            var sinceId = account.lastArticleId?.dollarLast() ?: ""
            var itemsBody = feverAPI.getItemsSince(sinceId)
            while (itemsBody.items?.isNotEmpty() == true) {
                val remoteItems = itemsBody.items.orEmpty()
                val articles = remoteItems.mapNotNull { item ->
                    val remoteItemId = item.id
                    val remoteFeedId = item.feed_id
                    if (remoteItemId == null || remoteFeedId == null) {
                        // Article and Feed ids are required by Room primary/foreign keys.
                        Log.w(TAG, "Skip incomplete Fever item id=$remoteItemId feedId=$remoteFeedId")
                        null
                    } else {
                        Article(
                            id = accountId.spacerDollar(remoteItemId),
                            date = item.created_on_time?.run { Date(this * 1000) } ?: Date(),
                            title = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                Html.fromHtml(item.title ?: context.getString(R.string.empty), Html.FROM_HTML_MODE_LEGACY).toString()
                            } else {
                                @Suppress("DEPRECATION")
                                Html.fromHtml(item.title ?: context.getString(R.string.empty)).toString()
                            },
                            author = item.author,
                            rawDescription = item.html ?: "",
                            shortDescription = (Readability4JExtended("", item.html ?: "")
                                .parse().textContent ?: "")
                                .take(110)
                                .trim(),
                            fullContent = item.html,
                            img = rssHelper.findImg(item.html ?: ""),
                            link = item.url ?: "",
                            feedId = accountId.spacerDollar(remoteFeedId),
                            accountId = accountId,
                            isUnread = (item.is_read ?: 0) <= 0,
                            isStarred = (item.is_saved ?: 0) > 0,
                            updateAt = Date(),
                        ).also {
                            sinceId = remoteItemId
                        }
                    }
                }
                articleDao.insert(*articles.toTypedArray())
                if (remoteItems.size >= FEVER_PAGE_SIZE) {
                    itemsBody = feverAPI.getItemsSince(sinceId)
                } else {
                    break
                }
            }

            // 4. Synchronize read/unread and starred/un-starred
            val unreadArticleIds = feverAPI.getUnreadItems().unread_item_ids?.split(",")
            val starredArticleIds = feverAPI.getSavedItems().saved_item_ids?.split(",")
            val articleMeta = articleDao.queryArticleMetadataAll(accountId)
            for (meta: ArticleMeta in articleMeta) {
                val articleId = meta.id.dollarLast()
                val shouldBeUnread = unreadArticleIds?.contains(articleId)
                val shouldBeStarred = starredArticleIds?.contains(articleId)
                if (meta.isUnread != shouldBeUnread) {
                    articleDao.markAsReadByArticleId(accountId, meta.id, shouldBeUnread ?: true)
                }
                if (meta.isStarred != shouldBeStarred) {
                    articleDao.markAsStarredByArticleId(accountId, meta.id, shouldBeStarred ?: false)
                }
            }


            Log.i("RLog", "onCompletion: ${System.currentTimeMillis() - preTime}")
            accountDao.update(account.apply {
                updateAt = Date()
                if (sinceId.isNotEmpty()) {
                    lastArticleId = accountId.spacerDollar(sinceId)
                }
            })
            ListenableWorker.Result.success(SyncWorker.setIsSyncing(false))
        } catch (e: Exception) {
            Log.e("RLog", "On sync exception: ${e.message}", e)
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
        val feverAPI = getFeverAPI()
        val beforeUnixTimestamp = (before?.time ?: Date(Long.MAX_VALUE).time) / 1000
        when {
            groupId != null -> {
                feverAPI.markGroup(
                    status = if (isUnread) FeverDTO.StatusEnum.Unread else FeverDTO.StatusEnum.Read,
                    id = groupId.dollarLast().toLong(),
                    before = beforeUnixTimestamp
                )
            }

            feedId != null -> {
                feverAPI.markFeed(
                    status = if (isUnread) FeverDTO.StatusEnum.Unread else FeverDTO.StatusEnum.Read,
                    id = feedId.dollarLast().toLong(),
                    before = beforeUnixTimestamp
                )
            }

            articleId != null -> {
                feverAPI.markItem(
                    status = if (isUnread) FeverDTO.StatusEnum.Unread else FeverDTO.StatusEnum.Read,
                    id = articleId.dollarLast(),
                )
            }

            else -> {
                feedDao.queryAll(context.currentAccountId).forEach {
                    feverAPI.markFeed(
                        status = if (isUnread) FeverDTO.StatusEnum.Unread else FeverDTO.StatusEnum.Read,
                        id = it.id.dollarLast().toLong(),
                        before = beforeUnixTimestamp
                    )
                }
            }
        }
    }

    override suspend fun markAsStarred(articleId: String, isStarred: Boolean) {
        super.markAsStarred(articleId, isStarred)
        val feverAPI = getFeverAPI()
        feverAPI.markItem(
            status = if (isStarred) FeverDTO.StatusEnum.Saved else FeverDTO.StatusEnum.Unsaved,
            id = articleId.dollarLast()
        )
    }

    private companion object {
        const val TAG = "RLog"

        // Fever returns at most 50 items per page; a full page means more items may follow.
        const val FEVER_PAGE_SIZE = 50
    }
}
