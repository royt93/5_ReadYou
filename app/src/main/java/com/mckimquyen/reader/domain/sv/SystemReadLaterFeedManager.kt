package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemReadLaterFeedManager @Inject constructor(
    private val feedDao: FeedDao,
    private val groupDao: GroupDao,
) {
    suspend fun ensureReadLaterFeed(accountId: Int, fallbackTitle: String): Feed {
        val existing = feedDao.queryById(Feed.READ_LATER_FEED_ID)
        if (existing != null) return existing

        val groups = groupDao.queryAll(accountId)
        val targetGroupId = if (groups.isNotEmpty()) {
            groups.first().id
        } else {
            val newGroup = Group(id = UUID.randomUUID().toString(), name = fallbackTitle, accountId = accountId)
            groupDao.insert(newGroup)
            newGroup.id
        }

        val feed = Feed(
            id = Feed.READ_LATER_FEED_ID,
            name = fallbackTitle,
            url = Feed.READ_LATER_URL,
            groupId = targetGroupId,
            accountId = accountId,
            sourceType = Feed.READ_LATER_SOURCE_TYPE,
            sourceUrl = Feed.READ_LATER_URL,
        )
        feedDao.insert(feed)
        return feed
    }
}
