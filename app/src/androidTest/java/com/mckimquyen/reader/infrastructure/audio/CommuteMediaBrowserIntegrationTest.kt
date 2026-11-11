package com.mckimquyen.reader.infrastructure.audio

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Verifies [CommuteMediaSessionService]'s [MediaBrowserServiceCompat] contract on a real device:
 * - A client can connect through [MediaBrowserCompat]
 * - [MediaBrowserServiceCompat.onGetRoot] returns a valid root
 * - [MediaBrowserServiceCompat.onLoadChildren] delivers the browsable CommuteCast item
 * - [MediaControllerCompat.TransportControls] can drive playback (playFromMediaId, fastForward, rewind, pause)
 *
 * This is the exact contract Android Auto and Android Automotive OS use to populate their car UI.
 */
@RunWith(AndroidJUnit4::class)
class CommuteMediaBrowserIntegrationTest {

    private lateinit var context: Context
    private var mediaBrowser: MediaBrowserCompat? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        mediaBrowser?.let {
            if (it.isConnected) it.disconnect()
        }
        mediaBrowser = null
        CommuteMediaSessionService.stop(context)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun mediaBrowser_connectsAndDeliversRoot() {
        val connectedLatch = CountDownLatch(1)
        var connected = false

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val componentName = ComponentName(context, CommuteMediaSessionService::class.java)
            val callback = object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    connected = true
                    connectedLatch.countDown()
                }

                override fun onConnectionFailed() {
                    connectedLatch.countDown()
                }
            }
            mediaBrowser = MediaBrowserCompat(context, componentName, callback, null).apply {
                connect()
            }
        }

        assertTrue("MediaBrowser must connect within 5s", connectedLatch.await(5, TimeUnit.SECONDS))
        assertTrue("MediaBrowser reported successful onConnected()", connected)

        val root = mediaBrowser?.root
        assertEquals(CommuteMediaSessionService.MEDIA_ROOT_ID, root)
    }

    @Test
    fun mediaBrowser_subscribesAndReceivesPlayableEpisodeItem() {
        val connectedLatch = CountDownLatch(1)
        val childrenLatch = CountDownLatch(1)
        var receivedItems: List<MediaBrowserCompat.MediaItem>? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val componentName = ComponentName(context, CommuteMediaSessionService::class.java)
            val callback = object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    connectedLatch.countDown()
                    mediaBrowser?.subscribe(
                        AutoMediaIds.COMMUTE_BRANCH,
                        object : MediaBrowserCompat.SubscriptionCallback() {
                            override fun onChildrenLoaded(
                                parentId: String,
                                children: MutableList<MediaBrowserCompat.MediaItem>,
                            ) {
                                receivedItems = children
                                childrenLatch.countDown()
                            }
                        }
                    )
                }
            }
            mediaBrowser = MediaBrowserCompat(context, componentName, callback, null).apply {
                connect()
            }
        }

        assertTrue("Connection latch timed out", connectedLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Children loaded latch timed out", childrenLatch.await(5, TimeUnit.SECONDS))

        assertNotNull("Must deliver children list", receivedItems)
        // If an episode was already persisted by DJ-05/06/07, it delivers 1 playable item; if empty,
        // it delivers emptyList(), neither of which crashes.
        val item = receivedItems?.firstOrNull()
        if (item != null) {
            assertTrue("Item must be playable on car UI", item.isPlayable)
            assertEquals(CommuteMediaSessionService.MEDIA_ID_LATEST_EPISODE, item.mediaId)
        }
    }

    @Test
    fun mediaController_transportControlsReachServiceWithoutCrash() {
        val connectedLatch = CountDownLatch(1)
        var controller: MediaControllerCompat? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val componentName = ComponentName(context, CommuteMediaSessionService::class.java)
            val callback = object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    val token = mediaBrowser?.sessionToken
                    if (token != null) {
                        controller = MediaControllerCompat(context, token)
                    }
                    connectedLatch.countDown()
                }
            }
            mediaBrowser = MediaBrowserCompat(context, componentName, callback, null).apply {
                connect()
            }
        }

        assertTrue("Connection latch timed out", connectedLatch.await(5, TimeUnit.SECONDS))
        assertNotNull("MediaController token must be available", controller)

        // Exercise the 4 car-control commands that map to our TTS engine. None of these should throw.
        controller?.transportControls?.fastForward()
        controller?.transportControls?.rewind()
        controller?.transportControls?.pause()
        controller?.transportControls?.playFromMediaId(
            CommuteMediaSessionService.MEDIA_ID_LATEST_EPISODE,
            null,
        )

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private class ChildrenResult(val items: List<MediaBrowserCompat.MediaItem>?, val errored: Boolean)

    /** Connects, subscribes to [parentId] and waits up to 5 s for either children or an error. */
    private fun loadChildren(parentId: String): ChildrenResult {
        val connected = CountDownLatch(1)
        val done = CountDownLatch(1)
        var items: List<MediaBrowserCompat.MediaItem>? = null
        var errored = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val componentName = ComponentName(context, CommuteMediaSessionService::class.java)
            val callback = object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    connected.countDown()
                    mediaBrowser?.subscribe(
                        parentId,
                        object : MediaBrowserCompat.SubscriptionCallback() {
                            override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
                                items = children
                                done.countDown()
                            }

                            override fun onError(parentId: String) {
                                errored = true
                                done.countDown()
                            }
                        },
                    )
                }
            }
            mediaBrowser = MediaBrowserCompat(context, componentName, callback, null).apply { connect() }
        }
        assertTrue("MediaBrowser must connect within 5s", connected.await(5, TimeUnit.SECONDS))
        assertTrue("Children of $parentId must arrive within 5s", done.await(5, TimeUnit.SECONDS))
        return ChildrenResult(items, errored)
    }

    @Test
    fun root_listsTheTwoBrowsableBranchesInOrder() {
        val result = loadChildren(CommuteMediaSessionService.MEDIA_ROOT_ID)

        val items = requireNotNull(result.items) { "root must deliver children" }
        assertEquals(listOf(AutoMediaIds.COMMUTE_BRANCH, AutoMediaIds.PODCAST_BRANCH), items.map { it.mediaId })
        assertTrue("both branches must be browsable", items.all { it.isBrowsable })
        assertTrue("branches are not playable themselves", items.none { it.isPlayable })
        assertTrue("titles must not be blank", items.all { !it.description.title.isNullOrBlank() })
    }

    @Test
    fun commuteBranch_hasAtMostOnePlayableLatestEpisode() {
        val items = requireNotNull(loadChildren(AutoMediaIds.COMMUTE_BRANCH).items)

        assertTrue("at most one item", items.size <= 1)
        items.forEach {
            assertTrue(it.isPlayable)
            assertEquals(AutoMediaIds.COMMUTE_EPISODE, it.mediaId)
        }
    }

    @Test
    fun podcastBranch_hasAtMostFivePlayableItemsAndNeverExposesAnAudioUrl() {
        val items = requireNotNull(loadChildren(AutoMediaIds.PODCAST_BRANCH).items)

        assertTrue("at most ${AutoMediaIds.MAX_PODCAST_ITEMS} items, got ${items.size}", items.size <= AutoMediaIds.MAX_PODCAST_ITEMS)
        items.forEach {
            assertTrue("playable leaf", it.isPlayable)
            assertTrue("id must carry the podcast prefix: ${it.mediaId}", it.mediaId.orEmpty().startsWith(AutoMediaIds.PODCAST_PREFIX))
            assertNull("the tree must not carry an audio URI", it.description.mediaUri)
            val extras: Bundle? = it.description.extras
            extras?.keySet().orEmpty().forEach { key ->
                assertFalse("extra $key must not hold a URL", extras?.get(key).toString().contains("://"))
            }
        }
    }

    @Test
    fun unknownParent_isAnErrorNotAnEmptyList() {
        val result = loadChildren("not_a_real_parent")

        assertTrue("unknown parents must report onError, as before", result.errored)
        assertNull(result.items)
    }

    @Test
    fun playFromMediaId_withUnknownOrHostilePodcastIds_startsNothing() {
        val connected = CountDownLatch(1)
        var controller: MediaControllerCompat? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val componentName = ComponentName(context, CommuteMediaSessionService::class.java)
            val callback = object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    mediaBrowser?.sessionToken?.let { controller = MediaControllerCompat(context, it) }
                    connected.countDown()
                }
            }
            mediaBrowser = MediaBrowserCompat(context, componentName, callback, null).apply { connect() }
        }
        assertTrue(connected.await(5, TimeUnit.SECONDS))
        val transport = requireNotNull(controller).transportControls

        listOf("podcast:front04-does-not-exist", "podcast:", "podcast:../../x' OR 1=1 --", "something-else").forEach {
            transport.playFromMediaId(it, null)
        }
        Thread.sleep(SETTLE_MS)

        val state = requireNotNull(controller).playbackState?.state
        assertTrue(
            "nothing may be playing or buffering after unknown ids, was $state",
            state == null || (state != PlaybackStateCompat.STATE_PLAYING && state != PlaybackStateCompat.STATE_BUFFERING),
        )
    }

    private companion object {
        const val SETTLE_MS = 1_500L
    }
}
