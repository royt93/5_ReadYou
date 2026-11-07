package com.mckimquyen.reader.ui.page.p2psync

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.p2psync.LocalSyncApplyResult
import com.mckimquyen.reader.domain.p2psync.LocalSyncPlan
import com.mckimquyen.reader.domain.p2psync.SyncPackage
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
class LocalSyncPageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val emptyPackage = SyncPackage(1, "a".repeat(32), 1, emptyList(), emptyList())
    private val emptyPlan = LocalSyncPlan(emptyList(), 0, 0, 0, emptyList())
    private var starts = 0
    private var loads = 0
    private var cancels = 0
    private var stops = 0
    private var applies = 0
    private val copiedUrls = mutableListOf<String>()
    private val copiedSecrets = mutableListOf<String>()
    private val sharedUrls = mutableListOf<String>()
    private val sharedSecrets = mutableListOf<String>()
    private var back = 0

    private fun show(
        state: LocalSyncUiState,
        isLocal: Boolean = true,
        rule: ComposeContentTestRule = compose,
    ) = rule.setContent {
        LocalSyncPage(
            state = state,
            isLocal = isLocal,
            onBack = { back++ },
            onStartSend = { starts++ },
            onLoadPreview = { _, _ -> loads++ },
            onCancel = { cancels++ },
            onStopSend = { stops++ },
            onApply = { applies++ },
            onCopyUrl = { copiedUrls += it },
            onCopySecret = { copiedSecrets += it },
            onShareUrl = { sharedUrls += it },
            onShareSecret = { sharedSecrets += it },
        )
    }

    @Test fun backButtonCallsCallback() {
        show(LocalSyncUiState.Idle)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
        compose.runOnIdle { assertEquals(1, back) }
    }

    @Test fun idleShowsSendAndReceiveAndSecretIsNotRendered() {
        show(LocalSyncUiState.Idle)
        compose.onNodeWithTag(SYNC_SEND_START_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SYNC_RECEIVE_TAB_TAG).assertIsDisplayed()
        compose.onAllNodesWithTag(SYNC_SECRET_VALUE_TAG).assertCountEquals(0)
    }

    @Test fun secretAppearsOnlyInSendingAndCopyAndShareStaySeparate() {
        val url = "http://192.168.1.2:5000/s/${"c".repeat(32)}"
        val secret = "0123456789abcdef".repeat(2)
        show(LocalSyncUiState.Sending(url, secret, 1_000L))
        compose.onNodeWithTag(SYNC_SECRET_VALUE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(SYNC_COPY_URL_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SYNC_COPY_SECRET_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SYNC_SHARE_URL_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SYNC_SHARE_SECRET_TAG).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(url), copiedUrls)
            assertEquals(listOf(secret), copiedSecrets)
            assertEquals(listOf(url), sharedUrls)
            assertEquals(listOf(secret), sharedSecrets)
        }
    }

    @Test fun stopSessionClosesTheSendSessionAndIsNotACancelOfAPreview() {
        show(LocalSyncUiState.Sending("http://192.168.1.2:5000/s/${"c".repeat(32)}", "0".repeat(32), 1_000L))
        compose.onNodeWithTag(SYNC_STOP_SEND_TAG).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Stop session must reach the callback that closes the server", 1, stops)
            assertEquals("Stop session must not be routed to the preview-cancel callback", 0, cancels)
        }
    }

    @Test fun cancelWhileStartingStopsTheSendSessionToo() {
        show(LocalSyncUiState.Starting)
        compose.onNodeWithTag(SYNC_CANCEL_START_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, stops); assertEquals(0, cancels) }
    }

    @Test fun startCallsCallbackOnlyWhenEnabled() {
        show(LocalSyncUiState.Idle, isLocal = false)
        compose.onNodeWithTag(SYNC_SEND_START_TAG).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, starts) }
    }

    @Test fun receiveUsesTypedUrlAndSecretFieldsAndDoesNotSubmitUntilLoad() {
        show(LocalSyncUiState.Idle)
        compose.onNodeWithTag(SYNC_RECEIVE_TAB_TAG).performClick()
        compose.onNodeWithTag(SYNC_URL_FIELD_TAG).performTextInput("http://192.168.1.2:5000/s/${"c".repeat(32)}")
        compose.onNodeWithTag(SYNC_SECRET_FIELD_TAG).performTextInput("0".repeat(32))
        compose.runOnIdle { assertEquals(0, loads) }
        compose.onNodeWithTag(SYNC_LOAD_PREVIEW_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, loads) }
    }

    @Test fun previewRequiresSecondApplyConfirmation() {
        show(LocalSyncUiState.Preview(emptyPackage, emptyPlan))
        compose.onNodeWithTag(SYNC_APPLY_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SYNC_CONFIRM_APPLY_TAG).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, applies) }
        compose.onNodeWithTag(SYNC_CONFIRM_APPLY_TAG).performClick()
        compose.runOnIdle { assertEquals(1, applies) }
    }

    @Test fun cancelInPreviewNeverApplies() {
        show(LocalSyncUiState.Preview(emptyPackage, emptyPlan))
        compose.onNodeWithTag(SYNC_CANCEL_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, cancels); assertEquals(0, applies) }
    }

    @Test fun errorStateHasNoApplyAction() {
        show(LocalSyncUiState.Error(LocalSyncError.PACKAGE), isLocal = true)
        compose.onAllNodesWithTag(SYNC_APPLY_TAG).assertCountEquals(0)
    }

    @Test fun nonLocalPreviewCannotApply() {
        show(LocalSyncUiState.Preview(emptyPackage, emptyPlan), isLocal = false)
        compose.onNodeWithTag(SYNC_APPLY_TAG).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, applies) }
    }

    @Test fun httpWarningIsAlwaysVisibleAndSecretWarningAppearsWithTheSecret() {
        show(LocalSyncUiState.Idle)
        compose.onNodeWithTag(SYNC_HTTP_WARNING_TAG).assertIsDisplayed()
        compose.onAllNodesWithTag(SYNC_SECRET_WARNING_TAG).assertCountEquals(0)
    }

    @Test fun sendingShowsTheSeparateChannelWarningNextToTheSecret() {
        show(LocalSyncUiState.Sending("http://192.168.1.2:5000/s/${"c".repeat(32)}", "0".repeat(32), 1_000L))
        compose.onNodeWithTag(SYNC_SECRET_WARNING_TAG).performScrollTo().assertIsDisplayed()
    }
}
