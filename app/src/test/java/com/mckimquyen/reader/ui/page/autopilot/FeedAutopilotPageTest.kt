package com.mckimquyen.reader.ui.page.autopilot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotSuggestion
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
class FeedAutopilotPageTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var backClicks = 0
    private var toggledEnabled: Boolean? = null
    private val unsubRequests = mutableListOf<String>()
    private val snoozeRequests = mutableListOf<String>()
    private val keepRequests = mutableListOf<String>()
    private var confirmClicks = 0
    private var cancelClicks = 0
    private var retryClicks = 0

    private fun render(
        state: FeedAutopilotUiState,
        enabled: Boolean = true,
        pendingUnsubscribe: PendingUnsubscribe? = null,
    ) = compose.setContent {
        FeedAutopilotPage(
            state = state,
            enabled = enabled,
            pendingUnsubscribe = pendingUnsubscribe,
            onBack = { backClicks++ },
            onToggleEnabled = { toggledEnabled = it },
            onRequestUnsubscribe = { unsubRequests += it },
            onSnooze = { snoozeRequests += it },
            onKeep = { keepRequests += it },
            onConfirmUnsubscribe = { confirmClicks++ },
            onCancelUnsubscribe = { cancelClicks++ },
            onRetry = { retryClicks++ },
        )
    }

    private fun suggestion(id: String, name: String, total: Int = 30, engaged: Int = 0) = FeedAutopilotSuggestion(
        feedId = id, feedName = name, feedIcon = null, windowTotal = total, windowEngaged = engaged,
        engagementRatio = engaged.toFloat() / total, engagementPercent = (engaged * 100) / total,
        windowDays = 30, feedAgeDays = 25,
    )

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    @Test fun backButtonInvokesCallback() {
        render(FeedAutopilotUiState.Clean(windowDays = 30))
        compose.onNodeWithContentDescription(str(R.string.back)).performClick()
        assertEquals(1, backClicks)
    }

    @Test fun toggleSwitchFlipsEnabledState() {
        render(FeedAutopilotUiState.Clean(windowDays = 30), enabled = true)
        compose.onNodeWithTag(AUTOPILOT_SWITCH_TAG).performClick()
        assertEquals(false, toggledEnabled)
    }

    @Test fun cleanStateShowsTidyMessage() {
        render(FeedAutopilotUiState.Clean(windowDays = 30))
        compose.onNodeWithText(str(R.string.feed_autopilot_summary_clean)).assertIsDisplayed()
    }

    @Test fun rendersSuggestionCardWithEngagementText() {
        render(
            FeedAutopilotUiState.HasSuggestions(
                suggestions = listOf(suggestion("fa", "Feed Alpha", total = 20, engaged = 0)),
                windowDays = 30,
            )
        )
        compose.onNodeWithText("Feed Alpha").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.feed_autopilot_reason_zero, 20, 30)).assertIsDisplayed()
        compose.onNodeWithTag("$AUTOPILOT_CARD_TAG_PREFIX" + "fa").assertIsDisplayed()
    }

    @Test fun tappingSnoozeAndKeepInvokeCallbacksWithFeedId() {
        render(
            FeedAutopilotUiState.HasSuggestions(
                suggestions = listOf(suggestion("fa", "Feed Alpha")),
                windowDays = 30,
            )
        )
        compose.onNodeWithTag("$AUTOPILOT_SNOOZE_TAG_PREFIX" + "fa").performScrollTo().performClick()
        assertEquals(listOf("fa"), snoozeRequests)

        compose.onNodeWithTag("$AUTOPILOT_KEEP_TAG_PREFIX" + "fa").performScrollTo().performClick()
        assertEquals(listOf("fa"), keepRequests)
    }

    @Test fun tappingUnsubscribeInvokesCallbackWithFeedId() {
        render(
            FeedAutopilotUiState.HasSuggestions(
                suggestions = listOf(suggestion("fa", "Feed Alpha")),
                windowDays = 30,
            )
        )
        compose.onNodeWithTag("$AUTOPILOT_UNSUB_TAG_PREFIX" + "fa").performScrollTo().performClick()
        assertEquals(listOf("fa"), unsubRequests)
    }

    @Test fun confirmationDialogShowsFeedNameAndStarredWarning() {
        render(
            state = FeedAutopilotUiState.HasSuggestions(listOf(suggestion("fa", "Feed Alpha")), windowDays = 30),
            pendingUnsubscribe = PendingUnsubscribe("fa", "Feed Alpha", starredCount = 4),
        )
        compose.onNodeWithTag(AUTOPILOT_CONFIRM_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.feed_autopilot_confirm_title, "Feed Alpha")).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.feed_autopilot_confirm_starred_warning, 4)).assertIsDisplayed()

        compose.onNodeWithText(str(R.string.cancel)).performClick()
        assertEquals(1, cancelClicks)
    }

    @Test fun confirmingDialogInvokesConfirmCallback() {
        render(
            state = FeedAutopilotUiState.HasSuggestions(listOf(suggestion("fa", "Feed Alpha")), windowDays = 30),
            pendingUnsubscribe = PendingUnsubscribe("fa", "Feed Alpha", starredCount = 0),
        )
        compose.onNodeWithTag(AUTOPILOT_CONFIRM_BUTTON_TAG).performClick()
        assertEquals(1, confirmClicks)
    }

    @Test fun notLocalAccountShowsItsMessage() {
        render(FeedAutopilotUiState.NotLocalAccount)
        compose.onNodeWithText(str(R.string.feed_autopilot_not_local_account)).assertIsDisplayed()
    }

    @Test fun shortRetentionShowsItsMessage() {
        render(FeedAutopilotUiState.RetentionTooShort)
        compose.onNodeWithText(str(R.string.feed_autopilot_retention_too_short)).assertIsDisplayed()
    }

    @Test fun disabledShowsItsMessage() {
        render(FeedAutopilotUiState.Disabled, enabled = false)
        compose.onNodeWithText(str(R.string.feed_autopilot_disabled_message)).assertIsDisplayed()
    }

    @Test fun errorStateShowsRetryButton() {
        render(FeedAutopilotUiState.Error)
        compose.onNodeWithText(str(R.string.feed_autopilot_error)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.retry)).performClick()
        assertEquals(1, retryClicks)
    }
}
