package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemReadLaterFeedManagerTest {
    private val feedDao = mockk<FeedDao>()
    private val groupDao = mockk<GroupDao>()
    private val manager = SystemReadLaterFeedManager(feedDao, groupDao)

    @Test
    fun returnsExistingFeedIfAlreadyPresent() = runBlocking {
        val existing = Feed(
            id = Feed.READ_LATER_FEED_ID,
            name = "Read Later",
            url = Feed.READ_LATER_URL,
            groupId = "group-1",
            accountId = 1,
            sourceType = Feed.READ_LATER_SOURCE_TYPE
        )
        coEvery { feedDao.queryById(Feed.READ_LATER_FEED_ID) } returns existing

        val result = manager.ensureReadLaterFeed(accountId = 1, fallbackTitle = "Read Later")
        assertEquals(existing, result)
        coVerify(exactly = 0) { feedDao.insert(any()) }
    }

    @Test
    fun createsNewFeedUsingFirstGroupWhenMissing() = runBlocking {
        coEvery { feedDao.queryById(Feed.READ_LATER_FEED_ID) } returns null
        coEvery { groupDao.queryAll(1) } returns listOf(Group(id = "default-group", name = "Default", accountId = 1))
        coEvery { feedDao.insert(any()) } returns Unit

        val result = manager.ensureReadLaterFeed(accountId = 1, fallbackTitle = "Đọc sau")
        assertEquals(Feed.READ_LATER_FEED_ID, result.id)
        assertEquals("Đọc sau", result.name)
        assertEquals("default-group", result.groupId)
        assertEquals(Feed.READ_LATER_SOURCE_TYPE, result.sourceType)
        coVerify(exactly = 1) { feedDao.insert(match { it.id == Feed.READ_LATER_FEED_ID }) }
    }
}
