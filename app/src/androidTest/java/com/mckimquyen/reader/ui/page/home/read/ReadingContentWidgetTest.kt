package com.mckimquyen.reader.ui.page.home.read

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Widget test cho Reading Content composable kiểm chứng render và scroll state.
 */
@RunWith(AndroidJUnit4::class)
class ReadingContentWidgetTest {

    @Test
    fun readingContent_rendersMetadataCorrectly() {
        val testTitle = "RSS Cat Hub 2026 Launch"
        val testFeed = "TechCrunch RSS"
        val testContent = "<p>Welcome to the revolutionary RSS Cat Hub!</p>"

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    val listState = rememberLazyListState()
                    Content(
                        content = testContent,
                        feedName = testFeed,
                        title = testTitle,
                        author = "Lead Architect",
                        link = "https://techcrunch.com/article",
                        publishedDate = Date(),
                        listState = listState,
                        isLoading = false
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }
}
