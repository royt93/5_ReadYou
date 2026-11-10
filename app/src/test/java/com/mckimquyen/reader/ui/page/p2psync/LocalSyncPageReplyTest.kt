package com.mckimquyen.reader.ui.page.p2psync

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.p2psync.LocalSyncApplyResult
import com.mckimquyen.reader.domain.p2psync.PairedPeer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocalSyncPageReplyTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private val peerId = "a".repeat(32)
    private val servedUrl = "http://192.168.1.2:5000/s/${"d".repeat(32)}"
    private val result = LocalSyncApplyResult(0, 1, 1, 0, 1)

    private fun show(
        state: LocalSyncUiState,
        replyPeerLabel: String? = null,
        peers: List<PairedPeer> = emptyList(),
        isLocal: Boolean = true,
    ) {
        compose.setContent {
            LocalSyncPage(
                state = state, isLocal = isLocal, onBack = {}, onStartSend = {}, onLoadPreview = { _, _ -> },
                onCancel = {}, onStopSend = { calls += "stop" }, onApply = {}, onCopyUrl = {}, onCopySecret = {},
                onShareUrl = {}, onShareSecret = {},
                pairedPeers = peers,
                replyPeerLabel = replyPeerLabel,
                onWaitForReply = { calls += "wait" },
                onReplyToPeer = { calls += "reply" },
            )
        }
    }

    /** Like the real screen: waiting closes the session, so the state returns to Idle after the callback. */
    private fun showWithLiveState(initial: LocalSyncUiState) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            LocalSyncPage(
                state = state, isLocal = true, onBack = {}, onStartSend = {}, onLoadPreview = { _, _ -> },
                onCancel = {}, onStopSend = {}, onApply = {}, onCopyUrl = {}, onCopySecret = {},
                onShareUrl = {}, onShareSecret = {},
                onWaitForReply = { calls += "wait"; state = LocalSyncUiState.Idle },
            )
        }
    }

    private fun text(id: Int) = compose.activity.getString(id)

    // ---- Stop and wait to receive back ----

    @Test fun aPairedSendOffersStopAndWaitWithAHint() {
        show(LocalSyncUiState.Sending(servedUrl, null, 1_000L, peerId))
        compose.onNodeWithTag(SYNC_ROLE_HINT_TAG).performScrollTo().assertIsDisplayed()
            .assertTextContains(text(R.string.local_sync_role_hint))
        compose.onNodeWithTag(SYNC_WAIT_REPLY_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("wait"), calls) }
    }

    @Test fun waitingClosesTheSessionViewAndLandsOnTheReceiveTab() {
        showWithLiveState(LocalSyncUiState.Sending(servedUrl, null, 1_000L, peerId))
        compose.onNodeWithTag(SYNC_WAIT_REPLY_TAG).performScrollTo().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("wait"), calls) }
        compose.onNodeWithTag(SYNC_URL_FIELD_TAG).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag(SYNC_PEER_SERVE_TAG + peerId).assertCountEquals(0)
    }

    @Test fun waitingKeepsTheOrdinaryStopButton() {
        show(LocalSyncUiState.Sending(servedUrl, null, 1_000L, peerId))
        compose.onNodeWithTag(SYNC_STOP_SEND_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("stop"), calls) }
    }

    @Test fun aManualSendHasNoWaitButtonAndNoRoleHint() {
        show(LocalSyncUiState.Sending(servedUrl, "0123456789abcdef".repeat(2), 1_000L, null))
        compose.onAllNodesWithTag(SYNC_WAIT_REPLY_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SYNC_ROLE_HINT_TAG).assertCountEquals(0)
    }

    // ---- Send back ----

    @Test fun afterApplyTheReplyBlockNamesThePeerAndSaysTheChangesWereLocal() {
        show(LocalSyncUiState.Done(result), replyPeerLabel = "Tablet")
        compose.onNodeWithTag(SYNC_REPLY_TAG).performScrollTo().assertIsDisplayed()
            .assertTextContains("Tablet", substring = true)
        compose.onNodeWithTag(SYNC_REPLY_NOTE_TAG).assertTextContains(text(R.string.local_sync_reply_note_applied))
    }

    @Test fun afterCancelTheNoteSaysTheChangesWereNotApplied() {
        show(LocalSyncUiState.Idle, replyPeerLabel = "Tablet")
        compose.onNodeWithTag(SYNC_REPLY_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SYNC_REPLY_NOTE_TAG).assertTextContains(text(R.string.local_sync_reply_note_cancelled))
    }

    @Test fun replyingCallsBackExactlyOnce() {
        show(LocalSyncUiState.Done(result), replyPeerLabel = "Tablet")
        compose.onNodeWithTag(SYNC_REPLY_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("reply"), calls) }
    }

    @Test fun noReplyBlockWithoutAReplyPeerLabel() {
        show(LocalSyncUiState.Done(result), replyPeerLabel = null)
        compose.onAllNodesWithTag(SYNC_REPLY_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(SYNC_REPLY_NOTE_TAG).assertCountEquals(0)
    }

    @Test fun noReplyBlockWhileBusyOrWithAnError() {
        show(LocalSyncUiState.Receiving, replyPeerLabel = "Tablet")
        compose.onAllNodesWithTag(SYNC_REPLY_TAG).assertCountEquals(0)
    }

    @Test fun replyIsDisabledWhenTheAccountIsNotLocal() {
        show(LocalSyncUiState.Done(result), replyPeerLabel = "Tablet", isLocal = false)
        compose.onNodeWithTag(SYNC_REPLY_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test fun theReplyBlockShowsTheInstruction() {
        show(LocalSyncUiState.Done(result), replyPeerLabel = "Tablet")
        compose.onNodeWithTag(SYNC_REPLY_INSTRUCTION_TAG).performScrollTo().assertIsDisplayed()
            .assertTextContains(text(R.string.local_sync_reply_instruction))
    }

    // ---- Honest limits ----

    @Test fun theMergeLimitIsShownOnTheIdleAndReplyScreensButNotWhileSending() {
        show(LocalSyncUiState.Idle)
        compose.onNodeWithTag(SYNC_MERGE_LIMIT_TAG).performScrollTo().assertIsDisplayed()
            .assertTextContains(text(R.string.local_sync_merge_limit))
    }

    @Test fun theMergeLimitAppearsOnceNextToTheReplyBlock() {
        show(LocalSyncUiState.Done(result), replyPeerLabel = "Tablet")
        compose.onAllNodesWithTag(SYNC_MERGE_LIMIT_TAG).assertCountEquals(1)
    }

    @Test fun noReplyStringClaimsBothPhonesAreDone() {
        val ids = listOf(
            R.string.local_sync_reply_note_applied, R.string.local_sync_reply_note_cancelled,
            R.string.local_sync_reply_instruction, R.string.local_sync_role_hint, R.string.local_sync_wait_reply,
            R.string.local_sync_merge_limit,
        )
        val forbidden = listOf("both phones", "both devices", "fully synced", "hai máy đã", "đồng bộ hoàn tất")
        ids.map { text(it).lowercase() }.forEach { value ->
            forbidden.forEach { assertFalse("'$value' contains '$it'", value.contains(it)) }
        }
    }
}
