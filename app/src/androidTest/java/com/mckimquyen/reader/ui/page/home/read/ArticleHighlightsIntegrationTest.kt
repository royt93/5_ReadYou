package com.mckimquyen.reader.ui.page.home.read

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.ArticleHighlights
import com.mckimquyen.reader.infrastructure.ai.ArticleHighlightsExtractor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleHighlightsIntegrationTest {

    @Test
    fun endToEnd_extractHighlightsAndRenderFullMaterialYouCard() {
        val title = "Next-Gen Android Architecture and Material You Innovations"
        val rawArticle = """
            Android 17 ushers in a transformative era for mobile application design. By leveraging dynamic Monet color palette extraction, apps automatically adapt to user wallpaper nuances.
            
            Modern reactive architectures rely heavily on Jetpack Compose and StateFlow. Decoupled ViewModels ensure predictable unidirectional data flow while eliminating memory leaks across configuration changes.
            
            Performance profiling demonstrates a 20% reduction in frame latency when adopting baseline profiles. This enables fluid 120Hz refresh rates across all flagship devices.
        """.trimIndent()

        // 1. Verify offline heuristic extraction logic
        val highlights: ArticleHighlights = ArticleHighlightsExtractor.extractOfflineHighlights(
            title = title,
            plainText = rawArticle
        )

        assertTrue("Highlights TLDR must not be blank", highlights.tldr.isNotBlank())
        assertTrue("Highlights must produce key takeaways", highlights.keyTakeaways.isNotEmpty())
        assertTrue("Highlights must compute saved reading time", highlights.readingTimeSavedMin >= 1)
        assertTrue("Highlights should discover topic tags", highlights.tags.isNotEmpty())

        // 2. Verify complete Compose rendering in live Activity on Android 17 runtime
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    SummarySheetContent(
                        state = SummaryState.Success(highlights),
                        onRetry = {},
                        onClose = {},
                        onCopy = {},
                        onShare = {},
                        onForceOffline = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("ComposeView must attach successfully", composeView)
        }
        scenario.close()
    }
}
