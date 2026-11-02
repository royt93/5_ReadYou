package com.mckimquyen.reader.domain.curated

import com.mckimquyen.reader.domain.model.addedsource.AddedRssSource
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedWithArticle
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.AddedRssSourceDao
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Date

class RssBundleFeedGatewayTest {

    private val repository = mockk<AbstractRssRepository>(relaxed = true)
    private val rssHelper = mockk<RssHelper>()
    private val addedDao = mockk<AddedRssSourceDao>(relaxed = true)
    private val gateway = RssBundleFeedGateway(
        repository = repository,
        rssHelper = rssHelper,
        addedRssSourceDao = addedDao,
        accountId = ACCOUNT_ID,
    )

    private val source = CuratedSource("Catalog name", "https://a.example/rss")

    private fun fetched(name: String = "Feed title") = FeedWithArticle(
        feed = Feed(id = "fetched-id", name = name, url = source.url, groupId = "", accountId = ACCOUNT_ID, icon = "icon"),
        articles = listOf(
            Article(
                id = "art-1", date = Date(0L), title = "T", rawDescription = "", shortDescription = "",
                link = "https://a.example/1", feedId = "fetched-id", accountId = ACCOUNT_ID,
            ),
        ),
    )

    @Test
    fun subscribeFeed_putsTheFetchedFeedIntoTheGivenGroup_andTracksTheSource() = runTest {
        coEvery { rssHelper.searchFeed(source.url) } returns fetched()
        val feed = slot<Feed>()
        val articles = slot<List<Article>>()
        coEvery { repository.subscribe(capture(feed), capture(articles)) } returns Unit

        gateway.subscribeFeed(source, "group-9")

        assertEquals("group-9", feed.captured.groupId)
        assertEquals("Feed title", feed.captured.name)
        assertEquals(ACCOUNT_ID, feed.captured.accountId)
        assertEquals(1, articles.captured.size)
        val tracked = slot<AddedRssSource>()
        coVerify { addedDao.insertAddedSource(capture(tracked)) }
        assertEquals(source.url, tracked.captured.url)
        assertEquals(ACCOUNT_ID, tracked.captured.accountId)
    }

    @Test
    fun subscribeFeed_fallsBackToTheCatalogNameWhenTheFeedHasNoTitle() = runTest {
        coEvery { rssHelper.searchFeed(source.url) } returns fetched(name = "  ")
        val feed = slot<Feed>()
        coEvery { repository.subscribe(capture(feed), any()) } returns Unit

        gateway.subscribeFeed(source, "g")

        assertEquals("Catalog name", feed.captured.name)
    }

    @Test
    fun subscribeFeed_whenTheFeedCannotBeRead_throwsAndSavesNothing() = runTest {
        coEvery { rssHelper.searchFeed(source.url) } throws IOException("unreachable")

        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { gateway.subscribeFeed(source, "g") } }

        coVerify(exactly = 0) { repository.subscribe(any(), any()) }
        coVerify(exactly = 0) { addedDao.insertAddedSource(any()) }
    }

    @Test
    fun createGroup_delegatesToTheRepository() = runTest {
        coEvery { repository.addGroup("Tech") } returns "gid"

        assertEquals("gid", gateway.createGroup("Tech"))
    }

    @Test
    fun deleteGroup_removesTheGroupAndItsFeedsThroughTheRepository() = runTest {
        val deleted = slot<Group>()
        coEvery { repository.deleteGroup(capture(deleted)) } returns Unit

        gateway.deleteGroup("gid")

        assertEquals("gid", deleted.captured.id)
        assertEquals(ACCOUNT_ID, deleted.captured.accountId)
        assertTrue(deleted.isCaptured)
    }

    private companion object {
        const val ACCOUNT_ID = 1
    }
}
