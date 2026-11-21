package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.model.group.GroupWithFeed
import com.mckimquyen.reader.domain.sv.LocalRssSvFactory
import com.mckimquyen.reader.infrastructure.rss.OPMLDataSource
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.ext.currentAccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

private const val NPUB = "npub1mlcawle2vuw97dscxundkg6phev0atsa5t0vakzrys8hk5pt5evssm7a0a"
private const val DID = "did:plc:front02akopmlprobeprobeprobe"

/**
 * FRONT-OPML on the phone's REAL Room and REAL parser: a Bluesky and a Nostr source are written by the real export attributes, read back
 * by the real [OPMLDataSource], stored through the real [com.mckimquyen.reader.domain.repository.FeedDao], and read again. It proves the
 * file's attributes survive a real Room write, and that the stored source address finds the source again.
 *
 * It does NOT call OpmlSv.saveToString or saveToDatabase: those export and import the WHOLE account into the default group, which would
 * put rows into the owner's real data. Only probe rows (ids with the probe prefix, in a probe group) are written; [BlueskyGuardRig.cleanUp]
 * removes them and the owner's own feed and article counts are compared before and after.
 * NOT covered: OpmlSv itself on a real account, the file picker, other OPML readers.
 */
@RunWith(AndroidJUnit4::class)
class OpmlSocialSourceDeviceTest {
    private lateinit var rig: BlueskyGuardRig
    private val app: RApp get() = LocalRssSvFactory.app()
    private var realRowsBefore = Pair(-1L, -1L)

    @Before
    fun setUp() {
        check(app.currentAccountType == AccountType.Local.id) { "This probe runs for the Local account only" }
        rig = BlueskyGuardRig()
        runBlocking {
            rig.cleanUp()
            realRowsBefore = rig.realRowCounts()
            rig.createGroup()
        }
    }

    @After
    fun tearDown() {
        try {
            runBlocking { rig.cleanUp() }
        } finally {
            rig.close()
        }
    }

    private val probeGroup get() = Group(id = rig.groupId, name = "probe", accountId = app.currentAccountId)

    private fun outline(feed: Feed): String {
        val extra = OPMLDataSource.socialAttributes(feed).entries.joinToString("") { """ ${it.key}="${it.value}"""" }
        return """<outline text="${feed.name}" title="${feed.name}" xmlUrl="${feed.url}" htmlUrl="${feed.url}"$extra/>"""
    }

    private fun probeFeed(id: String, name: String, url: String, kind: String, source: String?) = Feed(
        id = "${BlueskyGuardRig.PROBE_PREFIX}$id", name = name, url = url, groupId = rig.groupId, accountId = app.currentAccountId,
        sourceType = kind, sourceUrl = source,
    )

    private suspend fun importIntoProbeGroup(feeds: List<Feed>): List<Feed> {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><opml version="1.0"><head><title>probe</title></head><body>${feeds.joinToString("") { outline(it) }}</body></opml>"""
        val parsed = OPMLDataSource(app, Dispatchers.IO).parseFileInputStream(ByteArrayInputStream(xml.toByteArray()), probeGroup)
        val read = parsed.first().feeds.toList()
        // The parser gives each source a random id and the default group; the probe needs its own ids and group to stay clean.
        read.forEachIndexed { index, feed ->
            feed.id = "${BlueskyGuardRig.PROBE_PREFIX}read_$index"
            feed.groupId = rig.groupId
        }
        app.androidDatabase.feedDao().insertList(read)
        return read
    }

    @Test
    fun aBlueskyAndANostrSource_surviveTheFileAndARealRoomWrite_withKindAndStoredAddress() = runBlocking {
        val bsky = probeFeed("b", "Bluesky probe", "bsky:alice-front02ak.bsky.social", Feed.BLUESKY_SOURCE_TYPE, "bsky:$DID")
        val nostr = probeFeed("n", "Nostr probe", "nostr:$NPUB", Feed.NOSTR_SOURCE_TYPE, "nostr:$NPUB")
        importIntoProbeGroup(listOf(bsky, nostr))
        val dao = app.androidDatabase.feedDao()
        val backBsky = dao.queryByLink(app.currentAccountId, bsky.url).single { it.id.startsWith(BlueskyGuardRig.PROBE_PREFIX) }
        val backNostr = dao.queryByLink(app.currentAccountId, nostr.url).single { it.id.startsWith(BlueskyGuardRig.PROBE_PREFIX) }
        assertEquals(Feed.BLUESKY_SOURCE_TYPE, backBsky.sourceType)
        assertEquals("bsky:$DID", backBsky.sourceUrl)
        assertEquals(Feed.NOSTR_SOURCE_TYPE, backNostr.sourceType)
        assertEquals("nostr:$NPUB", backNostr.sourceUrl)
    }

    @Test
    fun theStoredSourceAddress_findsTheSourceAgainInRealRoom() = runBlocking {
        val bsky = probeFeed("b2", "Bluesky probe 2", "bsky:bob-front02ak.bsky.social", Feed.BLUESKY_SOURCE_TYPE, "bsky:$DID")
        importIntoProbeGroup(listOf(bsky))
        val found = app.androidDatabase.feedDao().queryBySourceUrl(app.currentAccountId, "bsky:$DID")
        assertTrue("the real index must find the probe source by its stored address", found.any { it.id.startsWith(BlueskyGuardRig.PROBE_PREFIX) })
    }

    @Test
    fun aFileThatClaimsTheWrongKind_comesOutAsAPlainRssSourceInRealRoom() = runBlocking {
        val liar = probeFeed("liar", "Liar probe", "https://front02ak.example/feed.xml", Feed.BLUESKY_SOURCE_TYPE, "bsky:$DID")
        importIntoProbeGroup(listOf(liar))
        val back = app.androidDatabase.feedDao().queryByLink(app.currentAccountId, liar.url).single { it.id.startsWith(BlueskyGuardRig.PROBE_PREFIX) }
        assertEquals("a kind that does not fit its address is dropped", Feed.RSS_SOURCE_TYPE, back.sourceType)
        assertNull(back.sourceUrl)
    }

    @Test
    fun anOrdinaryRssSource_isWrittenWithoutAnySocialAttributes() = runBlocking {
        val rss = probeFeed("rss", "Rss probe", "https://front02ak.example/rss.xml", Feed.RSS_SOURCE_TYPE, null)
        assertTrue("an RSS outline carries no extra attribute", OPMLDataSource.socialAttributes(rss).isEmpty())
        importIntoProbeGroup(listOf(rss))
        val back = app.androidDatabase.feedDao().queryByLink(app.currentAccountId, rss.url).single { it.id.startsWith(BlueskyGuardRig.PROBE_PREFIX) }
        assertEquals(Feed.RSS_SOURCE_TYPE, back.sourceType)
    }

    @Test
    fun afterCleanUp_nothingOfTheProbeIsLeft_andTheRealDataIsUntouched() {
        runBlocking { importIntoProbeGroup(listOf(probeFeed("tmp", "Tmp probe", "bsky:tmp-front02ak.bsky.social", Feed.BLUESKY_SOURCE_TYPE, "bsky:$DID"))) }
        assertTrue(rig.probeRowCount() > 0)
        runBlocking { rig.cleanUp() }
        assertEquals(0L, rig.probeRowCount())
        assertEquals("the owner's own feeds and articles are exactly as before", realRowsBefore, rig.realRowCounts())
        assertNotNull(rig)
    }
}
