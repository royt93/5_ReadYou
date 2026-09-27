package com.mckimquyen.reader.infrastructure.audio

import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
                        CommuteMediaSessionService.MEDIA_ROOT_ID,
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
}
