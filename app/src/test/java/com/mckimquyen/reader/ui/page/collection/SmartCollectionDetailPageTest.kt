package com.mckimquyen.reader.ui.page.collection

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.collection.SmartCollection
import com.mckimquyen.reader.domain.model.collection.SmartCollectionWithCount
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.SmartCollectionDao
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SmartCollectionDetailPageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun sampleArticleWithFeed(id: String, title: String): ArticleWithFeed =
        ArticleWithFeed(
            article = Article(
                id = id,
                date = Date(),
                title = title,
                author = "Author",
                rawDescription = "raw",
                shortDescription = "short",
                fullContent = null,
                img = null,
                link = "https://example.com/$id",
                feedId = "feed-1",
                accountId = 0,
            ),
            feed = Feed(
                id = "feed-1",
                name = "Tech News",
                url = "https://example.com/feed",
                groupId = "group-1",
                accountId = 0,
            ),
        )

    @Test
    fun detailPage_emptyState_showsEmptyArticlesMessage() {
        val dao = mockk<SmartCollectionDao>()
        every { dao.observeCollectionsWithCount(any()) } returns MutableStateFlow(
            listOf(
                SmartCollectionWithCount(
                    collection = SmartCollection(id = "col-1", accountId = 0, name = "My AI", query = "AI"),
                    articleCount = 0,
                )
            )
        )
        every { dao.observeArticlesInCollection("col-1") } returns MutableStateFlow(emptyList())
        val viewModel = SmartCollectionsViewModel(app, dao, mockk(relaxed = true))

        composeTestRule.setContent {
            SmartCollectionDetailPage(
                navController = NavHostController(app),
                collectionId = "col-1",
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithText("My AI").assertIsDisplayed()
        composeTestRule.onNodeWithText("AI").assertIsDisplayed()
        val emptyMessage = app.getString(R.string.smart_collection_empty_articles)
        composeTestRule.onNodeWithText(emptyMessage).assertIsDisplayed()
    }

    @Test
    fun detailPage_populatedState_showsArticleTitlesAndFeedName() {
        val dao = mockk<SmartCollectionDao>()
        every { dao.observeCollectionsWithCount(any()) } returns MutableStateFlow(
            listOf(
                SmartCollectionWithCount(
                    collection = SmartCollection(id = "col-1", accountId = 0, name = "My AI", query = "AI"),
                    articleCount = 2,
                )
            )
        )
        every { dao.observeArticlesInCollection("col-1") } returns MutableStateFlow(
            listOf(
                sampleArticleWithFeed("art-1", "AI breakthrough announced"),
                sampleArticleWithFeed("art-2", "New chip architecture"),
            )
        )
        val viewModel = SmartCollectionsViewModel(app, dao, mockk(relaxed = true))

        composeTestRule.setContent {
            SmartCollectionDetailPage(
                navController = NavHostController(app),
                collectionId = "col-1",
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithText("AI breakthrough announced").assertIsDisplayed()
        composeTestRule.onNodeWithText("New chip architecture").assertIsDisplayed()
    }

    @Test
    fun detailPage_emissionRecomposesWithoutRecreatingObservedFlow() {
        val dao = mockk<SmartCollectionDao>()
        every { dao.observeCollectionsWithCount(any()) } returns MutableStateFlow(
            listOf(
                SmartCollectionWithCount(
                    collection = SmartCollection(id = "col-1", accountId = 0, name = "My AI", query = "AI"),
                    articleCount = 0,
                )
            )
        )
        val articlesFlow = MutableStateFlow(emptyList<ArticleWithFeed>())
        every { dao.observeArticlesInCollection("col-1") } answers {
            articlesFlow
        }
        val viewModel = SmartCollectionsViewModel(app, dao, mockk(relaxed = true))
        val emptyMessage = app.getString(R.string.smart_collection_empty_articles)

        composeTestRule.setContent {
            SmartCollectionDetailPage(
                navController = NavHostController(app),
                collectionId = "col-1",
                viewModel = viewModel,
            )
        }
        composeTestRule.onNodeWithText(emptyMessage).assertIsDisplayed()

        articlesFlow.value = listOf(sampleArticleWithFeed("art-1", "Observed update"))
        composeTestRule.waitUntil(timeoutMillis = UI_UPDATE_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText("Observed update").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Observed update").assertIsDisplayed()

        verify(exactly = 1) { dao.observeArticlesInCollection("col-1") }
    }

    private companion object {
        const val UI_UPDATE_TIMEOUT_MS = 5000L
    }
}
