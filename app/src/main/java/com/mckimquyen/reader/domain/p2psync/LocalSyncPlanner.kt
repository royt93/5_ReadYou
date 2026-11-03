package com.mckimquyen.reader.domain.p2psync

import com.mckimquyen.reader.domain.model.feed.Feed
import java.util.Locale
import java.util.UUID

data class SyncArticleSnapshot(
    val id: String,
    val accountId: Int,
    val feedUrl: String,
    val link: String,
    val isUnread: Boolean,
    val isStarred: Boolean,
    val readAt: Long?,
)

data class SyncFeedToAdd(val id: String, val name: String, val url: String, val groupId: String)
data class SyncArticleUpdate(val receiverArticleId: String, val isUnread: Boolean, val isStarred: Boolean, val readAt: Long?)
data class LocalSyncPlan(
    val feedsToAdd: List<SyncFeedToAdd>,
    val duplicateFeeds: Int,
    val matchedArticles: Int,
    val unmatchedArticles: Int,
    val updates: List<SyncArticleUpdate>,
)

object LocalSyncPlanner {
    fun plan(
        incoming: SyncPackage,
        receiverFeeds: List<Feed>,
        receiverArticles: List<SyncArticleSnapshot>,
        receiverAccountId: Int,
        defaultGroupId: String,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): LocalSyncPlan {
        require(receiverAccountId > 0)
        require(defaultGroupId.isNotBlank())
        require(incoming.feeds.size <= SyncLimits.MAX_FEEDS)
        require(incoming.articles.size <= SyncLimits.MAX_ARTICLE_STATES)

        val incomingFeeds = incoming.feeds.map { feed ->
            val normalized = requireNotNull(FeedUrlNormalizer.normalize(feed.url))
            feed.copy(url = normalized)
        }
        require(incomingFeeds.map { it.url }.toSet().size == incomingFeeds.size)
        val feedUrls = incomingFeeds.mapTo(HashSet()) { it.url }
        val articleKeys = incoming.articles.map { article ->
            val normalizedFeed = requireNotNull(FeedUrlNormalizer.normalize(article.feedUrl))
            require(normalizedFeed in feedUrls)
            normalizedFeed to article.link
        }
        require(articleKeys.toSet().size == articleKeys.size)

        val receiverByUrl = receiverFeeds.asSequence()
            .filter { it.accountId == receiverAccountId }
            .mapNotNull { feed -> FeedUrlNormalizer.normalize(feed.url)?.let { it to feed } }
            .sortedBy { it.second.id }
            .distinctBy { it.first }
            .associate { it }

        val occupiedNames = receiverFeeds.asSequence()
            .filter { it.accountId == receiverAccountId && it.groupId == defaultGroupId }
            .map { it.name.normalizedName() }
            .toMutableSet()
        val additions = mutableListOf<SyncFeedToAdd>()
        var duplicateFeeds = 0
        incomingFeeds.sortedBy { it.url }.forEach { feed ->
            if (feed.url in receiverByUrl || additions.any { it.url == feed.url }) {
                duplicateFeeds++
            } else {
                var name = feed.name
                var suffix = 2
                while (name.normalizedName() in occupiedNames) {
                    name = "${feed.name} (${suffix++})"
                }
                occupiedNames += name.normalizedName()
                additions += SyncFeedToAdd(newId(), name, feed.url, defaultGroupId)
            }
        }

        val articleIndex = receiverArticles.asSequence()
            .filter { it.accountId == receiverAccountId }
            .mapNotNull { snapshot -> FeedUrlNormalizer.normalize(snapshot.feedUrl)?.let { (it to snapshot.link) to snapshot } }
            .sortedBy { it.second.id }
            .distinctBy { it.first }
            .associate { it }

        var matched = 0
        var unmatched = 0
        val updates = incoming.articles.mapNotNull { state ->
            val key = requireNotNull(FeedUrlNormalizer.normalize(state.feedUrl)) to state.link
            val current = articleIndex[key]
            if (current == null) {
                unmatched++
                null
            } else {
                matched++
                val readAt = listOfNotNull(current.readAt, state.readAt).minOrNull()
                val unread = current.isUnread && state.isUnread
                val starred = current.isStarred || state.isStarred
                if (unread == current.isUnread && starred == current.isStarred && readAt == current.readAt) null
                else SyncArticleUpdate(current.id, unread, starred, readAt)
            }
        }.sortedBy { it.receiverArticleId }

        return LocalSyncPlan(additions, duplicateFeeds, matched, unmatched, updates)
    }

    private fun String.normalizedName(): String = trim().lowercase(Locale.ROOT)
}
