package com.mckimquyen.reader.domain.curated

import com.mckimquyen.reader.domain.model.addedsource.AddedRssSource
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.AddedRssSourceDao
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.infrastructure.rss.RssHelper

/**
 * Real [BundleFeedGateway] for one account: fetches each feed with [RssHelper.searchFeed] and saves it through the
 * account's repository. Unlike the single-source flow, a feed that cannot be read is NOT saved as an empty shell; it
 * throws so the bundle counts it as failed.
 */
class RssBundleFeedGateway(
    private val repository: AbstractRssRepository,
    private val rssHelper: RssHelper,
    private val addedRssSourceDao: AddedRssSourceDao,
    private val accountId: Int,
) : BundleFeedGateway {

    override suspend fun createGroup(name: String): String = repository.addGroup(name)

    override suspend fun deleteGroup(groupId: String) {
        repository.deleteGroup(Group(id = groupId, name = "", accountId = accountId))
    }

    override suspend fun subscribeFeed(source: CuratedSource, groupId: String) {
        val fetched = rssHelper.searchFeed(source.url)
        val feed = fetched.feed.copy(
            name = fetched.feed.name.ifBlank { source.name },
            groupId = groupId,
            accountId = accountId,
            isNotification = false,
            isFullContent = false,
        )
        repository.subscribe(feed, fetched.articles)
        addedRssSourceDao.insertAddedSource(AddedRssSource(url = source.url, name = source.name, accountId = accountId))
    }
}
