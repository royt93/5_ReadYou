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
import com.mckimquyen.reader.domain.p2psync.SyncArticleState
import com.mckimquyen.reader.domain.p2psync.SyncFeed
import com.mckimquyen.reader.domain.p2psync.SyncPackage
import com.mckimquyen.reader.domain.p2psync.SyncPackageCodec
import com.mckimquyen.reader.domain.p2psync.SyncPackageResult
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import com.mckimquyen.reader.ui.ext.getDefaultGroupId
import java.util.UUID

class LocalSyncRepository @Inject constructor(private val db: AndroidDatabase) {
    private var newId: () -> String = { UUID.randomUUID().toString() }

    internal constructor(db: AndroidDatabase, newId: () -> String) : this(db) {
        this.newId = newId
    }


    suspend fun snapshot(localAccountId: Int): SyncPackage {
        val account = db.accountDao().queryById(localAccountId)
        require(account?.type?.id == AccountType.Local.id)
        val feeds = db.feedDao().queryAll(localAccountId)
        val groups = db.groupDao().queryAll(localAccountId).associateBy { it.id }
        val packageFeeds = feeds.mapNotNull { feed ->
            FeedUrlNormalizer.normalize(feed.url)?.let { url ->
                SyncFeed(url, feed.name, groups[feed.groupId]?.name.orEmpty().ifEmpty { "Default" })
            }
        }
        val feedById = feeds.associateBy { it.id }
        val packageArticles = db.articleDao().querySyncSnapshots(localAccountId).mapNotNull { article ->
            val feedUrl = feedById.values.firstOrNull { FeedUrlNormalizer.normalize(it.url) == FeedUrlNormalizer.normalize(article.feedUrl) }
                ?.let { FeedUrlNormalizer.normalize(it.url) } ?: FeedUrlNormalizer.normalize(article.feedUrl)
            feedUrl?.let { SyncArticleState(it, article.link, article.isUnread, article.isStarred, article.readAt) }
        }
        val proposed = SyncPackage(1, "0".repeat(32), System.currentTimeMillis(), packageFeeds, packageArticles)
        val encoded = SyncPackageCodec.encode(proposed)
        return (SyncPackageCodec.decodeAndValidate(encoded) as SyncPackageResult.Valid).data
    }

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
}
