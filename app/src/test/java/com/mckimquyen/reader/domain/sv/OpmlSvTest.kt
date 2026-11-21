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
import org.junit.Assert.assertFalse
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

    private val npub = "npub1mlcawle2vuw97dscxundkg6phev0atsa5t0vakzrys8hk5pt5evssm7a0a"
    private val goodNostr = nostrFeed.copy(url = "nostr:$npub", sourceUrl = "nostr:$npub")

    @Test
    fun roundTrip_ofABlueskySource_comesBackAsABlueskySourceWithItsStoredAddress() = runBlocking {
        val back = exportThenImport(listOf(blueskyFeed)).single()
        assertEquals(blueskyFeed.url, back.url)
        assertEquals(Feed.BLUESKY_SOURCE_TYPE, back.sourceType)
        assertEquals(blueskyFeed.sourceUrl, back.sourceUrl)
    }

    @Test
    fun roundTrip_ofANostrSource_comesBackAsANostrSourceWithItsStoredAddress() = runBlocking {
        val back = exportThenImport(listOf(goodNostr)).single()
        assertEquals(goodNostr.url, back.url)
        assertEquals(Feed.NOSTR_SOURCE_TYPE, back.sourceType)
        assertEquals(goodNostr.sourceUrl, back.sourceUrl)
    }

    @Test
    fun export_writesTheTwoAttributesOnlyForBlueskyAndNostr() = runBlocking {
        coEvery { groupDao.queryById(any()) } returns defaultGroup
        coEvery { groupDao.queryAllGroupWithFeed(any()) } returns
            listOf(GroupWithFeed(defaultGroup, mutableListOf(rssFeed, blueskyFeed, goodNostr)))
        val xml = realOpmlSv().saveToString(accountId)
        assertEquals("only the two social outlines carry a sourceType", 2, Regex("""sourceType=""").findAll(xml).count())
        assertTrue(xml, xml.contains("sourceType=\"BLUESKY\""))
        assertTrue(xml, xml.contains("sourceType=\"NOSTR\""))
        assertFalse("an RSS outline is written as before", Regex("""xmlUrl="https://a.example/feed.xml"[^>]*sourceType""").containsMatchIn(xml))
    }

    private fun opmlWith(outline: String) =
        """<?xml version="1.0" encoding="UTF-8"?><opml version="1.0"><head><title>t</title></head><body>$outline</body></opml>"""

    private suspend fun importOf(outline: String): List<Feed> {
        coEvery { groupDao.queryById(any()) } returns defaultGroup
        coEvery { rssSv.get().isFeedExist(any()) } returns false
        coEvery { rssSv.get().isFeedSourceExist(any()) } returns false
        val inserted = slot<List<Feed>>()
        coEvery { feedDao.insertList(capture(inserted)) } returns emptyList()
        realOpmlSv().saveToDatabase(ByteArrayInputStream(opmlWith(outline).toByteArray()))
        return inserted.captured
    }

    @Test
    fun anOldFileWithoutTheAttributes_importsAsPlainRssLikeBefore() = runBlocking {
        val back = importOf("""<outline text="A" title="A" xmlUrl="bsky:alice.bsky.social"/>""").single()
        assertEquals(Feed.RSS_SOURCE_TYPE, back.sourceType)
        assertNull(back.sourceUrl)
    }

    @Test
    fun aSourceTypeThatDoesNotMatchTheAddress_isDroppedAndImportedAsRss() = runBlocking {
        val cases = listOf(
            """<outline text="A" title="A" xmlUrl="https://a.example/feed" sourceType="BLUESKY" sourceUrl="bsky:alice.bsky.social"/>""",
            """<outline text="A" title="A" xmlUrl="https://a.example/feed" sourceType="NOSTR" sourceUrl="nostr:$npub"/>""",
            """<outline text="A" title="A" xmlUrl="bsky:not a handle" sourceType="BLUESKY"/>""",
            """<outline text="A" title="A" xmlUrl="nostr:npub1notvalid" sourceType="NOSTR"/>""",
        )
        for (outline in cases) {
            val back = importOf(outline).single()
            assertEquals(outline, Feed.RSS_SOURCE_TYPE, back.sourceType)
            assertNull(outline, back.sourceUrl)
        }
    }

    @Test
    fun anUnknownOrDangerousSourceType_isIgnored_neverNewTypesFromTheFile() = runBlocking {
        for (kind in listOf("WEB", "READ_LATER", "bluesky", "", "RSS", "x\" onload=\"y", "BLUESKY ")) {
            val safe = kind.replace("\"", "&quot;")
            val back = importOf("""<outline text="A" title="A" xmlUrl="https://a.example/feed" sourceType="$safe"/>""").single()
            assertEquals("kind=[$kind]", Feed.RSS_SOURCE_TYPE, back.sourceType)
        }
    }

    @Test
    fun aValidSourceUrlOfTheWrongKind_isDropped() = runBlocking {
        val back = importOf("""<outline text="A" title="A" xmlUrl="bsky:alice.bsky.social" sourceType="BLUESKY" sourceUrl="nostr:$npub"/>""").single()
        assertEquals(Feed.BLUESKY_SOURCE_TYPE, back.sourceType)
        assertNull("the stored address must belong to the same kind", back.sourceUrl)
    }

    @Test
    fun aSocialSourceInsideAGroup_isRestoredToo() = runBlocking {
        val outline = """<outline text="G" title="G" isDefault="false">
            <outline text="B" title="B" xmlUrl="bsky:alice.bsky.social" sourceType="BLUESKY" sourceUrl="bsky:did:plc:alicealicealicealiceal"/>
        </outline>"""
        val back = importOf(outline).single { it.name == "B" }
        assertEquals(Feed.BLUESKY_SOURCE_TYPE, back.sourceType)
        assertEquals("bsky:did:plc:alicealicealicealiceal", back.sourceUrl)
    }

    @Test
    fun import_skipsASourceWhoseStoredSourceAddressIsAlreadyKnown_evenWithAnotherUrl() = runBlocking {
        coEvery { groupDao.queryById(any()) } returns defaultGroup
        coEvery { rssSv.get().isFeedExist(any()) } returns false
        coEvery { rssSv.get().isFeedSourceExist("bsky:did:plc:alicealicealicealiceal") } returns true
        val inserted = slot<List<Feed>>()
        coEvery { feedDao.insertList(capture(inserted)) } returns emptyList()
        val outline = """<outline text="B" title="B" xmlUrl="bsky:alice.bsky.social" sourceType="BLUESKY" sourceUrl="bsky:did:plc:alicealicealicealiceal"/>"""
        realOpmlSv().saveToDatabase(ByteArrayInputStream(opmlWith(outline).toByteArray()))
        assertTrue("the same account under its DID must not be imported twice", inserted.captured.isEmpty())
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
