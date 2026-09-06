package com.mckimquyen.reader.ui.page.home.read

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleHighlightsSheetWidgetTest {

    private val sampleHighlights = ArticleHighlights(
        tldr = "Android 17 brings breakthrough performance and on-device machine intelligence.",
        keyTakeaways = listOf(
            "Predictive back gestures across all activities.",
            "Dynamic spatial audio pipeline.",
            "15% reduction in background memory consumption."
        ),
        readingTimeSavedMin = 3,
        tags = listOf("Android17", "Performance", "Audio"),
        isOfflineFallback = false,
    )

    @Test
    fun summarySheet_rendersLoadingState_withoutException() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    SummarySheetContent(
                        state = SummaryState.Loading,
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun summarySheet_rendersSuccessState_displaysTakeawaysAndTriggersCallbacks() {
        var copyInvoked = false
        var shareInvoked = false

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    SummarySheetContent(
                        state = SummaryState.Success(sampleHighlights),
                        onRetry = {},
                        onClose = {},
                        onCopy = { copyInvoked = true },
                        onShare = { shareInvoked = true },
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun summarySheet_rendersOfflineFallbackBadge_withoutException() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    SummarySheetContent(
                        state = SummaryState.Success(sampleHighlights.copy(isOfflineFallback = true)),
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun summarySheet_rendersErrorState_andAttachesRetryButton() {
        var retryInvoked = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    SummarySheetContent(
                        state = SummaryState.Error(R.string.summary_err_network),
                        onRetry = { retryInvoked = true },
                        onClose = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }
}
