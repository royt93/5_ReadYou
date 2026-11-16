package com.mckimquyen.reader.domain.sv

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.feed.Feed
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One Bluesky source and one Nostr source go through the three steps of a background sync round (read the source,
 * apply the filter rules, insert what is new) against the phone's real Room, with fake servers on 127.0.0.1.
 * Deliberately NOT run: AbstractRssRepository.sync and SyncWorker.doWork, which walk every source the phone has over the real network.
 * NOT covered: precache, the semantic index, due reminders, notifications, WorkManager scheduling, a real network.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundSyncDeviceTest {
    private lateinit var rig: BackgroundSyncRig
    private var groupRowsBefore: Long = -1L

    @Before
    fun setUp() {
        rig = BackgroundSyncRig()
        runBlocking {
            rig.cleanUp()
            groupRowsBefore = rig.counts().group
            rig.addProbeSources()
        }
    }

    /** Runs even when a test failed: the probe sources must never stay in the phone's database. */
    @After
    fun tearDown() {
        try {
            runBlocking { rig.cleanUp() }
        } finally {
            rig.close()
        }
    }

    private fun feedBodyWithTwoOwnPosts() = BlueskyDeviceFixtures.feedBody(
        BlueskyDeviceFixtures.post("3k1", "first own post"),
        BlueskyDeviceFixtures.repost("3k2"),
        BlueskyDeviceFixtures.post("3k3", "second own post"),
    )

    private fun useBlueskyFeed(body: String) {
        rig.feedResponse = { MockResponse().setHeader("Content-Type", "application/json").setBody(body) }
    }

    private fun storedFeed(id: String): Feed = runBlocking { requireNotNull(rig.feedDao().queryById(id)) }

    @Test
    fun blueskySource_writesOnlyItsOwnPosts_withTheRightLinks() {
        useBlueskyFeed(feedBodyWithTwoOwnPosts())
        val result = runBlocking { rig.syncOnce(rig.blueskyFeed) }
        assertNotNull(result.outcome)
        assertEquals(setOf("first own post", "second own post"), result.inserted.map { it.title }.toSet())
        assertTrue(result.inserted.all { it.feedId == rig.blueskyFeed.id })
        assertTrue(result.inserted.all { it.link.startsWith("https://bsky.app/profile/${BlueskyDeviceFixtures.DID}/post/") })
        assertEquals(2, rig.articleCountOf(rig.blueskyFeed.id))
    }

    @Test
    fun blueskySource_isNeverRenamedNorGivenANewIcon_byTheSync() {
        useBlueskyFeed(feedBodyWithTwoOwnPosts())
        runBlocking { rig.syncOnce(rig.blueskyFeed) }
        val stored = storedFeed(rig.blueskyFeed.id)
        assertEquals("Bluesky · Probe", stored.name)
        assertEquals("https://cdn.bsky.app/img/avatar/probe@jpeg", stored.icon)
        assertTrue(stored.name != BlueskyDeviceFixtures.HOSTILE_NAME)
        assertTrue(stored.icon != BlueskyDeviceFixtures.HOSTILE_AVATAR)
    }

    @Test
    fun nostrSource_writesItsNote_withAnNjumpLink_andIsNeverRenamed() {
        val result = runBlocking { rig.syncOnce(rig.nostrFeed) }
        assertNotNull(result.outcome)
        assertEquals(1, result.inserted.size)
        assertTrue(result.inserted.single().link.startsWith("https://njump.me/note1"))
        assertEquals(rig.nostrFeed.id, result.inserted.single().feedId)
        val stored = storedFeed(rig.nostrFeed.id)
        assertEquals("Nostr · Probe", stored.name)
        assertEquals("https://example.invalid/probe.png", stored.icon)
    }

    @Test
    fun aSecondSync_ofTheSameAnswer_insertsNothingNew() {
        useBlueskyFeed(feedBodyWithTwoOwnPosts())
        assertEquals(2, runBlocking { rig.syncOnce(rig.blueskyFeed) }.inserted.size)
        assertEquals(0, runBlocking { rig.syncOnce(rig.blueskyFeed) }.inserted.size)
        assertEquals(2, rig.articleCountOf(rig.blueskyFeed.id))

        assertEquals(1, runBlocking { rig.syncOnce(rig.nostrFeed) }.inserted.size)
        rig.nostrRelay.serveAnotherConnection()
        assertEquals(0, runBlocking { rig.syncOnce(rig.nostrFeed) }.inserted.size)
        assertEquals(1, rig.articleCountOf(rig.nostrFeed.id))
    }

    @Test
    fun blueskyServerError_isASyncFailure_andWritesNoArticle() {
        rig.profileResponse = { MockResponse().setResponseCode(500) }
        val result = runBlocking { rig.syncOnce(rig.blueskyFeed) }
        assertNull(result.outcome)
        assertTrue(result.inserted.isEmpty())
        assertEquals(0, rig.articleCountOf(rig.blueskyFeed.id))
        assertEquals("Bluesky · Probe", storedFeed(rig.blueskyFeed.id).name)
    }

    @Test
    fun blueskyNotFound_isASyncFailure_andWritesNoArticle() {
        rig.profileResponse = {
            MockResponse().setResponseCode(400).setBody("""{"error":"InvalidRequest","message":"Profile not found"}""")
        }
        val result = runBlocking { rig.syncOnce(rig.blueskyFeed) }
        assertNull(result.outcome)
        assertEquals(0, rig.articleCountOf(rig.blueskyFeed.id))
    }

    @Test
    fun blueskyAnswerWithOnlyRepostsAndNoOwnPosts_isASyncFailure_notAnEmptyFeed() {
        useBlueskyFeed(BlueskyDeviceFixtures.feedBody(BlueskyDeviceFixtures.repost("3k2")))
        val result = runBlocking { rig.syncOnce(rig.blueskyFeed) }
        assertNull(result.outcome)
        assertEquals(0, rig.articleCountOf(rig.blueskyFeed.id))
    }

    @Test
    fun afterCleanUp_nothingOfTheProbeIsLeftAnywhere() {
        useBlueskyFeed(feedBodyWithTwoOwnPosts())
        runBlocking { rig.syncOnce(rig.blueskyFeed) }
        runBlocking { rig.syncOnce(rig.nostrFeed) }
        runBlocking { rig.cleanUp() }
        // The phone's own scheduled sync worker may add rows of OTHER feeds meanwhile, so whole-table counts are only reported, never compared.
        assertEquals("group rows must be back to what they were", groupRowsBefore, rig.counts().group)
        assertEquals(0L, rig.probeRowCount())
    }
}
