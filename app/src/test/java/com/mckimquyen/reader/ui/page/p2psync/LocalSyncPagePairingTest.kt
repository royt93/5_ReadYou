package com.mckimquyen.reader.ui.page.p2psync

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.mckimquyen.reader.domain.p2psync.PairedPeer
import com.mckimquyen.reader.domain.p2psync.PairingStatus
import com.mckimquyen.reader.infrastructure.p2psync.DiscoveredPeer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalSyncPagePairingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private val confirmed = PairedPeer("a".repeat(32), "Tablet", PairingStatus.CONFIRMED, 1L, false)
    private val unconfirmed = PairedPeer("b".repeat(32), "Phone", PairingStatus.UNCONFIRMED, 2L, false)
    private val broken = PairedPeer("c".repeat(32), "Old", PairingStatus.CONFIRMED, 3L, true)
    private val nearby = DiscoveredPeer("rsscathub-x", "192.168.1.9", 4000, ByteArray(16))

    private fun show(
        state: LocalSyncUiState = LocalSyncUiState.Idle,
        peers: List<PairedPeer> = emptyList(),
        found: List<DiscoveredPeer> = emptyList(),
        tabReceive: Boolean = false,
    ) {
        compose.setContent {
            LocalSyncPage(
                state = state, isLocal = true, onBack = {}, onStartSend = {}, onLoadPreview = { u, sec -> calls += "load:$u:$sec" },
                onLoadPreviewAndPair = { u, sec, label -> calls += "loadpair:$u:$sec:$label" },
                onCancel = {}, onStopSend = {}, onApply = {}, onCopyUrl = {}, onCopySecret = {},
                onShareUrl = {}, onShareSecret = {},
                pairedPeers = peers,
                nearby = found,
                onPairNew = { calls += "pair:$it" },
                onReceiveFromPeer = { id, peer -> calls += "receive:$id:${peer.host}" },
                onForgetPeer = { calls += "forget:$it" },
                onServePeer = { calls += "serve:$it" },
                onConfirmPeer = { calls += "confirm:$it" },
                onOpenSettings = { calls += "settings" },
                onRequestLanPermission = { calls += "allow" },
            )
        }
        if (tabReceive) compose.onNodeWithTag(SYNC_RECEIVE_TAB_TAG).performClick()
    }

    private val url = "http://192.168.1.2:5000/s/${"c".repeat(32)}"
    private val secretHex = "0123456789abcdef".repeat(2)

    private fun fillReceive(label: String?) {
        compose.onNodeWithTag(SYNC_URL_FIELD_TAG).performScrollTo().performTextInput(url)
        compose.onNodeWithTag(SYNC_SECRET_FIELD_TAG).performScrollTo().performTextInput(secretHex)
        label?.let { compose.onNodeWithTag(SYNC_RECEIVE_LABEL_TAG).performScrollTo().performTextInput(it) }
        compose.onNodeWithTag(SYNC_LOAD_PREVIEW_TAG).performScrollTo().performClick()
    }

    @Test fun receiveWithoutALabelUsesThePlainPreviewCallback() {
        show(tabReceive = true)
        fillReceive(null)
        compose.runOnIdle { assertEquals(listOf("load:$url:$secretHex"), calls) }
    }

    @Test fun receiveWithALabelAsksToRememberTheDevice() {
        show(tabReceive = true)
        fillReceive("Tablet")
        compose.runOnIdle { assertEquals(listOf("loadpair:$url:$secretHex:Tablet"), calls) }
    }

    @Test fun aBlankReceiveLabelIsTreatedAsNoLabel() {
        show(tabReceive = true)
        fillReceive("   ")
        compose.runOnIdle { assertEquals(listOf("load:$url:$secretHex"), calls) }
    }

    private val servedUrl = "http://192.168.1.2:5000/s/${"d".repeat(32)}"

    @Test fun aServedPairedDeviceShowsNoSecretBlockAndNoSecretButtons() {
        show(state = LocalSyncUiState.Sending(servedUrl, null, 1_000L, confirmed.peerId))
        compose.onNodeWithTag(SYNC_STOP_SEND_TAG).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag(SYNC_SECRET_VALUE_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SYNC_COPY_SECRET_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SYNC_SHARE_SECRET_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SYNC_SECRET_WARNING_TAG).assertCountEquals(0)
    }

    @Test fun aNewPairingStillShowsTheSecretBlock() {
        show(state = LocalSyncUiState.Sending(servedUrl, "0123456789abcdef".repeat(2), 1_000L, unconfirmed.peerId))
        compose.onNodeWithTag(SYNC_SECRET_VALUE_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test fun sendTabOffersServeForEachUsablePairedDevice() {
        show(peers = listOf(confirmed, broken))
        compose.onNodeWithTag(SYNC_PEER_SERVE_TAG + confirmed.peerId).performScrollTo().performClick()
        compose.onNodeWithTag(SYNC_PEER_SERVE_TAG + broken.peerId).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf("serve:${confirmed.peerId}"), calls) }
    }

    @Test fun sendTabWithoutPairedDevicesHasNoServeButtons() {
        show()
        compose.onAllNodesWithTag(SYNC_PEER_SERVE_TAG + confirmed.peerId).assertCountEquals(0)
    }

    @Test fun pairNewSendsTheTypedLabel() {
        show()
        compose.onNodeWithTag(SYNC_PAIR_LABEL_TAG).performScrollTo().performTextInput("Tablet")
        compose.onNodeWithTag(SYNC_PAIR_NEW_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("pair:Tablet"), calls) }
    }

    @Test fun pairNewIsDisabledForABlankLabel() {
        show()
        compose.onNodeWithTag(SYNC_PAIR_NEW_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test fun pairedPeersShowStatusAndForgetCallsBack() {
        show(peers = listOf(confirmed, unconfirmed), tabReceive = true)
        compose.onNodeWithTag(SYNC_PEER_ROW_TAG + confirmed.peerId).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SYNC_PEER_FORGET_TAG + confirmed.peerId).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("forget:${confirmed.peerId}"), calls) }
    }

    @Test fun onlyAnUnconfirmedPeerOffersTheConfirmButton() {
        show(peers = listOf(confirmed, unconfirmed), tabReceive = true)
        compose.onAllNodesWithTag(SYNC_PEER_CONFIRM_TAG + confirmed.peerId).assertCountEquals(0)
        compose.onNodeWithTag(SYNC_PEER_CONFIRM_TAG + unconfirmed.peerId).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("confirm:${unconfirmed.peerId}"), calls) }
    }

    @Test fun receiveFromPeerNeedsANearbyDeviceAndUsesIt() {
        show(peers = listOf(confirmed), tabReceive = true)
        compose.onNodeWithTag(SYNC_PEER_RECEIVE_TAG + confirmed.peerId).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(emptyList<String>(), calls) }
    }

    @Test fun receiveFromPeerUsesTheFirstNearbyDevice() {
        show(peers = listOf(confirmed), found = listOf(nearby), tabReceive = true)
        compose.onNodeWithTag(SYNC_PEER_RECEIVE_TAG + confirmed.peerId).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("receive:${confirmed.peerId}:192.168.1.9"), calls) }
    }

    @Test fun aPeerThatNeedsRepairCannotReceiveButCanBeForgotten() {
        show(peers = listOf(broken), found = listOf(nearby), tabReceive = true)
        compose.onNodeWithTag(SYNC_PEER_RECEIVE_TAG + broken.peerId).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(SYNC_PEER_FORGET_TAG + broken.peerId).performScrollTo().assertIsDisplayed()
    }

    @Test fun permissionErrorOffersAllowAndOpenSettings() {
        show(state = LocalSyncUiState.Error(LocalSyncError.LAN_PERMISSION))
        compose.onNodeWithTag(SYNC_ALLOW_LAN_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SYNC_OPEN_SETTINGS_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("allow", "settings"), calls) }
    }

    @Test fun otherErrorsDoNotOfferPermissionButtons() {
        show(state = LocalSyncUiState.Error(LocalSyncError.NETWORK))
        compose.onAllNodesWithTag(SYNC_ALLOW_LAN_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SYNC_OPEN_SETTINGS_TAG).assertCountEquals(0)
    }
}
