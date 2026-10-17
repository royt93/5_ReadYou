package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.model.article.Article
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebFeedPreviewWidgetTest {
    @get:Rule val composeRule = createComposeRule()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
        .allowMainThreadQueries().build()

    @org.junit.After
    fun tearDown() { db.close() }

    @Test
    fun savingDisablesConfirmAndCancel() {
        composeRule.setContent {
            WebFeedPreviewContent("Example", emptyList(), onConfirm = {}, onCancel = {}, saving = true)
        }
        composeRule.onNodeWithText("Add web feed").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test
    fun previewShowsCandidateTitlesAndExplicitConfirmCancelActions() {
        val articles = (1..3).map { index ->
            Article("a$index", Date(index.toLong()), "Candidate $index", rawDescription = "", shortDescription = "", link = "https://example.test/$index", feedId = "f", accountId = 1)
                .apply { link = "https://example.test/$index"; feedId = "f"; accountId = 1 }
        }
        var confirmed = 0
        var cancelled = 0

        composeRule.setContent {
            WebFeedPreviewContent(
                siteTitle = "Example Journal",
                articles = articles,
                onConfirm = { confirmed++ },
                onCancel = { cancelled++ },
            )
        }

        composeRule.onNodeWithText("Example Journal").assertIsDisplayed()
        articles.forEach { composeRule.onNodeWithText(it.title).assertIsDisplayed() }
        composeRule.onNodeWithText("Add web feed").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(1, confirmed)
        assertEquals(1, cancelled)
    }
}
