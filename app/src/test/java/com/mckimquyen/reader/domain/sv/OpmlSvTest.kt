package com.mckimquyen.reader.domain.sv

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.group.GroupWithFeed
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.infrastructure.rss.OPMLDataSource
import com.mckimquyen.reader.ui.ext.getDefaultGroupId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OpmlSvTest {

    private val groupDao = mockk<GroupDao>(relaxed = true)
    private val feedDao = mockk<FeedDao>(relaxed = true)
    private val accountDao = mockk<AccountDao>(relaxed = true)
    private val rssSv = mockk<RssSv>(relaxed = true)
    private val opmlDataSource = mockk<OPMLDataSource>(relaxed = true)
    private lateinit var opmlSv: OpmlSv

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        coEvery { groupDao.queryById(any()) } returns null
        opmlSv = OpmlSv(context, groupDao, feedDao, accountDao, rssSv, opmlDataSource)
    }

    @Test
    fun saveToDatabase_whenDefaultGroupMissing_throwsIllegalStateInsteadOfNpe() {
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                opmlSv.saveToDatabase(ByteArrayInputStream(ByteArray(0)))
            }
        }

        assertTrue(error.message.orEmpty().contains("Default group not found"))
        // Import must stop before parsing or writing anything.
        coVerify(exactly = 0) { opmlDataSource.parseFileInputStream(any(), any()) }
        coVerify(exactly = 0) { feedDao.insertList(any()) }
    }

    @Test
    fun saveToString_whenDefaultGroupMissing_throwsIllegalStateWithAccountId() {
        val accountId = 7

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { opmlSv.saveToString(accountId) }
        }

        assertTrue(error.message.orEmpty().contains("account $accountId"))
        coVerify(exactly = 0) { groupDao.queryAllGroupWithFeed(any()) }
    }

    // ---- Round trip: what an exported source looks like after it is imported again (measured, not assumed).

    private val accountId = 1
    private val defaultGroup = Group(id = accountId.getDefaultGroupId(), name = "Default", accountId = accountId)
    private val rssFeed = Feed(id = "f-rss", name = "A site", url = "https://a.example/feed.xml", groupId = defaultGroup.id, accountId = accountId)
    private val blueskyFeed = Feed(
        id = "f-bsky", name = "Bluesky · alice", url = "bsky:alice.bsky.social", groupId = defaultGroup.id, accountId = accountId,
        sourceType = Feed.BLUESKY_SOURCE_TYPE, sourceUrl = "bsky:did:plc:alicealicealicealiceal",
    )
    private val nostrFeed = Feed(
        id = "f-nostr", name = "Nostr · npub1xyz", url = "nostr:npub1xyz", groupId = defaultGroup.id, accountId = accountId,
        sourceType = Feed.NOSTR_SOURCE_TYPE, sourceUrl = "nostr:npub1xyz",
    )

    private fun realOpmlSv(): OpmlSv {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = OPMLDataSource(context, kotlinx.coroutines.Dispatchers.Unconfined)
        return OpmlSv(context, groupDao, feedDao, accountDao, rssSv, source)
    }

    private suspend fun exportThenImport(feeds: List<Feed>): List<Feed> {
        coEvery { groupDao.queryById(any()) } returns defaultGroup
        coEvery { groupDao.queryAllGroupWithFeed(any()) } returns listOf(GroupWithFeed(defaultGroup, feeds.toMutableList()))
        coEvery { rssSv.get().isFeedExist(any()) } returns false
        val sv = realOpmlSv()
        val xml = sv.saveToString(accountId)
        val inserted = slot<List<Feed>>()
        coEvery { feedDao.insertList(capture(inserted)) } returns emptyList()
        sv.saveToDatabase(ByteArrayInputStream(xml.toByteArray()))
        return inserted.captured
    }

    @Test
    fun roundTrip_ofAnRssSource_comesBackAsTheSameRssSource() = runBlocking {
        val back = exportThenImport(listOf(rssFeed)).single()
        assertEquals(rssFeed.url, back.url)
        assertEquals(Feed.RSS_SOURCE_TYPE, back.sourceType)
    }

    @Test
    fun roundTrip_ofABlueskyOrNostrSource_reportsWhatTheyBecome() = runBlocking {
        val back = exportThenImport(listOf(blueskyFeed, nostrFeed))
        val bsky = back.single { it.name == blueskyFeed.name }
        val nostr = back.single { it.name == nostrFeed.name }
        // Measured behaviour today (a finding, not an endorsement): the address survives, the kind and the stored source address do not.
        assertEquals(blueskyFeed.url, bsky.url)
        assertEquals(nostrFeed.url, nostr.url)
        println("ROUNDTRIP bluesky sourceType=${bsky.sourceType} sourceUrl=${bsky.sourceUrl}")
        println("ROUNDTRIP nostr sourceType=${nostr.sourceType} sourceUrl=${nostr.sourceUrl}")
        assertEquals(Feed.RSS_SOURCE_TYPE, bsky.sourceType)
        assertEquals(Feed.RSS_SOURCE_TYPE, nostr.sourceType)
        assertNull(bsky.sourceUrl)
        assertNull(nostr.sourceUrl)
    }

    @Test
    fun import_skipsASourceWhoseAddressIsAlreadyStored() = runBlocking {
        coEvery { groupDao.queryById(any()) } returns defaultGroup
        coEvery { groupDao.queryAllGroupWithFeed(any()) } returns listOf(GroupWithFeed(defaultGroup, mutableListOf(blueskyFeed)))
        val sv = realOpmlSv()
        val xml = sv.saveToString(accountId)
        coEvery { rssSv.get().isFeedExist(blueskyFeed.url) } returns true
        val inserted = slot<List<Feed>>()
        coEvery { feedDao.insertList(capture(inserted)) } returns emptyList()
        sv.saveToDatabase(ByteArrayInputStream(xml.toByteArray()))
        assertTrue("the stored source must not be inserted a second time", inserted.captured.isEmpty())
    }

    @Test
    fun roundTrip_keepsTheCaseOfAnAddress_becauseAnAddressIsCaseSensitive() = runBlocking {
        val mixed = rssFeed.copy(id = "f-case", name = "Mixed case", url = "https://A.example/Feed/Atom.XML?Id=AbC")
        val back = exportThenImport(listOf(mixed)).single()
        assertEquals("https://A.example/Feed/Atom.XML?Id=AbC", back.url)
    }
}
