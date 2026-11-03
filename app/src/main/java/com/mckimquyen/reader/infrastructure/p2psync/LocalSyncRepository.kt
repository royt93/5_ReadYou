package com.mckimquyen.reader.infrastructure.p2psync

import androidx.room.withTransaction
import javax.inject.Inject
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.p2psync.FeedUrlNormalizer
import com.mckimquyen.reader.domain.p2psync.LocalSyncApplyResult
import com.mckimquyen.reader.domain.p2psync.LocalSyncPlan
import com.mckimquyen.reader.domain.p2psync.LocalSyncPlanner
import com.mckimquyen.reader.domain.p2psync.SyncArticleSnapshot
import com.mckimquyen.reader.domain.p2psync.SyncLimits
import com.mckimquyen.reader.domain.p2psync.SyncArticleState
import com.mckimquyen.reader.domain.p2psync.SyncFeed
import com.mckimquyen.reader.domain.p2psync.SyncPackage
import com.mckimquyen.reader.domain.p2psync.SyncPackageCodec
import com.mckimquyen.reader.domain.p2psync.SyncPackageResult
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.ui.ext.getDefaultGroupId
import java.net.URI
import java.util.UUID

class LocalSyncRepository @Inject constructor(private val db: AndroidDatabase) {
    private var newId: () -> String = { UUID.randomUUID().toString() }

    internal constructor(db: AndroidDatabase, newId: () -> String) : this(db) {
        this.newId = newId
    }

    /**
     * Builds the outgoing package from allowlisted columns only. Dirty local rows are repaired or dropped here, so one odd
     * feed name or link can never make Send fail. Articles without state (unread, unstarred, no readAt) are not sent:
     * the monotonic merge can never change anything from them.
     */
    suspend fun snapshot(localAccountId: Int): SyncPackage {
        requireLocal(localAccountId)
        val groups = db.groupDao().queryAll(localAccountId).associate { it.id to it.name }
        val feeds = db.feedDao().queryAll(localAccountId)
            .sortedBy { it.id }
            .mapNotNull { feed ->
                val url = FeedUrlNormalizer.normalize(feed.url) ?: return@mapNotNull null
                SyncFeed(
                    url,
                    cleanLabel(feed.name, fallback = url),
                    cleanLabel(groups[feed.groupId].orEmpty(), fallback = DEFAULT_GROUP_LABEL),
                )
            }
            .distinctBy { it.url }
            .take(SyncLimits.MAX_FEEDS)
        val feedUrls = feeds.mapTo(HashSet()) { it.url }
        val articles = db.articleDao().querySyncSnapshots(localAccountId)
            .asSequence()
            .filter { !(it.isUnread && !it.isStarred && it.readAt == null) }
            .mapNotNull { article ->
                val feedUrl = FeedUrlNormalizer.normalize(article.feedUrl)?.takeIf { it in feedUrls } ?: return@mapNotNull null
                if (!isSendableLink(article.link)) return@mapNotNull null
                SyncArticleState(feedUrl, article.link, article.isUnread, article.isStarred, article.readAt?.takeIf { it >= 0 })
            }
            .groupBy { it.feedUrl to it.link }
            .values
            .map { group ->
                group.reduce { left, right ->
                    left.copy(
                        isUnread = left.isUnread && right.isUnread,
                        isStarred = left.isStarred || right.isStarred,
                        readAt = listOfNotNull(left.readAt, right.readAt).minOrNull(),
                    )
                }
            }
            .sortedWith(compareBy(SyncArticleState::feedUrl, SyncArticleState::link))
            .take(SyncLimits.MAX_ARTICLE_STATES)
        val proposed = SyncPackage(1, newSourceId(), System.currentTimeMillis(), feeds, articles)
        return (SyncPackageCodec.decodeAndValidate(SyncPackageCodec.encode(proposed)) as SyncPackageResult.Valid).data
    }

    private fun cleanLabel(raw: String, fallback: String): String {
        val cleaned = raw.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().take(SyncLimits.MAX_NAME_CHARS).trim()
        return cleaned.ifEmpty { fallback.take(SyncLimits.MAX_NAME_CHARS) }
    }

    private fun isSendableLink(link: String): Boolean = link.length <= SyncLimits.MAX_ARTICLE_LINK_CHARS &&
        link.none { it.isWhitespace() || it.isISOControl() } &&
        runCatching { URI(link) }.getOrNull()?.let {
            (it.scheme.equals("http", ignoreCase = true) || it.scheme.equals("https", ignoreCase = true)) &&
                !it.host.isNullOrEmpty() && it.rawUserInfo == null
        } == true

    private fun newSourceId(): String = UUID.randomUUID().toString().replace("-", "")

    suspend fun preview(localAccountId: Int, incoming: SyncPackage): LocalSyncPlan {
        requireLocal(localAccountId)
        val feeds = db.feedDao().queryAll(localAccountId)
        val articles = db.articleDao().querySyncSnapshots(localAccountId)
        return makePlan(localAccountId, incoming, feeds, articles)
    }

    suspend fun apply(localAccountId: Int, incoming: SyncPackage): LocalSyncApplyResult = db.withTransaction {
        requireLocal(localAccountId)
        val plan = makePlan(
            localAccountId,
            incoming,
            db.feedDao().queryAll(localAccountId),
            db.articleDao().querySyncSnapshots(localAccountId),
        )
        val feeds = plan.feedsToAdd.map { item ->
            Feed(item.id, item.name, url = item.url, groupId = item.groupId, accountId = localAccountId)
        }
        if (feeds.isNotEmpty()) db.feedDao().insert(*feeds.toTypedArray())
        plan.updates.forEach { update ->
            val count = db.articleDao().updateSyncState(
                localAccountId, update.receiverArticleId, update.isUnread, update.isStarred, update.readAt,
            )
            check(count == 1) { "Article changed while applying sync" }
        }
        LocalSyncApplyResult(
            feedsAdded = feeds.size,
            duplicateFeeds = plan.duplicateFeeds,
            matchedArticles = plan.matchedArticles,
            unmatchedArticles = plan.unmatchedArticles,
            updatesApplied = plan.updates.size,
        )
    }

    private suspend fun requireLocal(accountId: Int) {
        val account = db.accountDao().queryById(accountId)
        require(account?.type?.id == AccountType.Local.id)
        check(db.groupDao().queryById(accountId.getDefaultGroupId())?.accountId == accountId) {
            "Default group missing for Local account"
        }
    }

    private fun makePlan(
        accountId: Int,
        incoming: SyncPackage,
        feeds: List<Feed>,
        articles: List<SyncArticleSnapshot>,
    ): LocalSyncPlan = LocalSyncPlanner.plan(
        incoming = incoming,
        receiverFeeds = feeds,
        receiverArticles = articles,
        receiverAccountId = accountId,
        defaultGroupId = accountId.getDefaultGroupId(),
        newId = newId,
    )

    private companion object {
        const val DEFAULT_GROUP_LABEL = "Default"
    }
}
