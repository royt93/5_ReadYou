package com.mckimquyen.reader.ui.page.setting.feedhealth

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedErrorType
import com.mckimquyen.reader.domain.model.feed.FeedHealthRecord
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FeedHealthCardWidgetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleFeed = Feed(
        id = "feed_1",
        name = "VnExpress Tech",
        url = "https://vnexpress.net/rss/so-hoa.rss",
        groupId = "g1",
        accountId = 1,
    )

    @Test
    fun feedHealthCard_healthyStatus_rendersHealthyBadge() {
        val item = FeedHealthItem(
            feed = sampleFeed,
            record = FeedHealthRecord(
                feedId = "feed_1",
                lastSuccessTime = 1000L,
                lastLatencyMs = 215L,
                lastErrorType = FeedErrorType.NONE,
            ),
            isRetrying = false,
        )

        composeRule.setContent {
            FeedHealthCard(item = item, onRetry = {})
        }

        composeRule.onNodeWithText("VnExpress Tech").assertIsDisplayed()
        composeRule.onNodeWithText("Healthy").assertIsDisplayed()
        composeRule.onNodeWithText("Latency: 215 ms").assertIsDisplayed()
    }

    @Test
    fun feedHealthCard_failingStatus_rendersErrorBadgeAndMessage() {
        val item = FeedHealthItem(
            feed = sampleFeed,
            record = FeedHealthRecord(
                feedId = "feed_1",
                lastSuccessTime = null,
                lastLatencyMs = 5000L,
                lastErrorType = FeedErrorType.TIMEOUT,
                lastErrorMessage = "SocketTimeoutException connecting to server",
                lastErrorTime = 2000L,
            ),
            isRetrying = false,
        )

        composeRule.setContent {
            FeedHealthCard(item = item, onRetry = {})
        }

        composeRule.onNodeWithText("VnExpress Tech").assertIsDisplayed()
        composeRule.onNodeWithText("TIMEOUT").assertIsDisplayed()
        composeRule.onNodeWithText("SocketTimeoutException connecting to server").assertIsDisplayed()
    }

    @Test
    fun feedHealthCard_clickRetry_triggersCallback() {
        var retried = false
        val item = FeedHealthItem(
            feed = sampleFeed,
            record = null,
            isRetrying = false,
        )

        composeRule.setContent {
            FeedHealthCard(item = item, onRetry = { retried = true })
        }

        composeRule.onNodeWithText("Retry Now").performClick()
        assertEquals(true, retried)
    }
}
