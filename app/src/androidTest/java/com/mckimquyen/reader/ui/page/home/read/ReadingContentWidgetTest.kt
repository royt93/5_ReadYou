package com.mckimquyen.reader.ui.page.home.read

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

/**
 * Widget test cho Reading Content composable kiểm chứng render và scroll state.
 */
@RunWith(AndroidJUnit4::class)
class ReadingContentWidgetTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readingContent_rendersMetadataCorrectly() {
        val testTitle = "RSS Cat Hub 2026 Launch"
        val testFeed = "TechCrunch RSS"
        val testContent = "<p>Welcome to the revolutionary RSS Cat Hub!</p>"

        rule.setContent {
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

        rule.onNodeWithText(testTitle).assertIsDisplayed()
        rule.onNodeWithText(testFeed).assertIsDisplayed()
    }
}
