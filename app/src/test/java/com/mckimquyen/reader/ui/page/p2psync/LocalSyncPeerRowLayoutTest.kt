package com.mckimquyen.reader.ui.page.p2psync

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.mckimquyen.reader.domain.p2psync.PairedPeer
import com.mckimquyen.reader.domain.p2psync.PairingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On a real 360 dp phone (Tecno KJ7) the three actions of a paired-device row shared one row, 96 dp each, so labels such as
 * "Mark as paired" wrapped and were clipped by the fixed 48 dp height. Robolectric does not measure text like a device, so
 * this checks the structure that causes it: each action must get the full card width and sit on its own line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalSyncPeerRowLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val unconfirmed = PairedPeer("b".repeat(32), "PixelPhone", PairingStatus.UNCONFIRMED, 2L, false)
    private val confirmed = PairedPeer("c".repeat(32), "Tablet", PairingStatus.CONFIRMED, 3L, false)
    private val tags = listOf(SYNC_PEER_RECEIVE_TAG, SYNC_PEER_CONFIRM_TAG, SYNC_PEER_FORGET_TAG)

    private fun showReceiveTab(peer: PairedPeer) {
        compose.setContent {
            LocalSyncPage(
                state = LocalSyncUiState.Idle, isLocal = true, onBack = {}, onStartSend = {}, onLoadPreview = { _, _ -> },
                onCancel = {}, onStopSend = {}, onApply = {}, onCopyUrl = {}, onCopySecret = {},
                onShareUrl = {}, onShareSecret = {},
                pairedPeers = listOf(peer),
            )
        }
        compose.onNodeWithTag(SYNC_RECEIVE_TAB_TAG).performClick()
    }

    private fun bounds(prefix: String, peer: PairedPeer) =
        compose.onNodeWithTag(prefix + peer.peerId).performScrollTo().getUnclippedBoundsInRoot()

    @Test fun eachActionGetsAtLeastTwoThirdsOfTheScreenWidth() {
        showReceiveTab(unconfirmed)
        tags.forEach { prefix ->
            val b = bounds(prefix, unconfirmed)
            assertTrue("$prefix is only ${b.width} wide", b.width >= 240.dp)
        }
    }

    @Test fun actionsAreStackedOnSeparateLinesInOrder() {
        showReceiveTab(unconfirmed)
        val tops = tags.map { bounds(it, unconfirmed).top }
        assertEquals(tops.sorted(), tops)
        assertEquals("no two actions share a line", tops.size, tops.toSet().size)
    }

    @Test fun actionsStayInsideTheScreen() {
        showReceiveTab(unconfirmed)
        tags.forEach { prefix ->
            val b = bounds(prefix, unconfirmed)
            assertTrue("$prefix ends at ${b.right}", b.right <= 360.dp)
            assertTrue("$prefix starts at ${b.left}", b.left >= 0.dp)
        }
    }

    @Test fun everyActionKeepsTheMinimumTouchHeight() {
        showReceiveTab(unconfirmed)
        tags.forEach { assertTrue(bounds(it, unconfirmed).height >= 48.dp) }
    }

    @Test fun aConfirmedPeerStillStacksItsTwoActions() {
        showReceiveTab(confirmed)
        val receive = bounds(SYNC_PEER_RECEIVE_TAG, confirmed)
        val forget = bounds(SYNC_PEER_FORGET_TAG, confirmed)
        assertTrue(receive.width >= 240.dp && forget.width >= 240.dp)
        assertTrue(forget.top > receive.top)
    }
}
