package com.mckimquyen.reader.ui.page.collection

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.navigation.NavHostController
import com.mckimquyen.reader.domain.model.collection.SmartCollection
import com.mckimquyen.reader.domain.model.collection.SmartCollectionWithCount
import com.mckimquyen.reader.domain.repository.SmartCollectionDao
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SmartCollectionsPageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun page_emptyState_isDisplayed() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = mockk<SmartCollectionDao>()
        every { dao.observeCollectionsWithCount(any()) } returns MutableStateFlow(emptyList())
        val viewModel = SmartCollectionsViewModel(app, dao, mockk(relaxed = true))

        composeTestRule.setContent {
            SmartCollectionsPage(navController = NavHostController(app), viewModel = viewModel)
        }

        composeTestRule.onNodeWithTag("smart_collections_empty").assertIsDisplayed()
    }

    @Test
    fun page_populatedState_showsCollectionAndCount() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = mockk<SmartCollectionDao>()
        every { dao.observeCollectionsWithCount(any()) } returns MutableStateFlow(
            listOf(
                SmartCollectionWithCount(
                    collection = SmartCollection(
                        id = "col-ai",
                        accountId = 0,
                        name = "AI updates",
                        query = "AI in Europe",
                    ),
                    articleCount = 7,
                )
            )
        )
        val viewModel = SmartCollectionsViewModel(app, dao, mockk(relaxed = true))

        composeTestRule.setContent {
            SmartCollectionsPage(navController = NavHostController(app), viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("AI updates").assertIsDisplayed()
        composeTestRule.onNodeWithText("AI in Europe").assertIsDisplayed()
    }

    @Test
    fun page_populatedState_displaysArticleCount() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = mockk<SmartCollectionDao>()
        every { dao.observeCollectionsWithCount(any()) } returns MutableStateFlow(
            listOf(
                SmartCollectionWithCount(
                    collection = SmartCollection(
                        id = "col-ai",
                        accountId = 0,
                        name = "AI updates",
                        query = "AI in Europe",
                    ),
                    articleCount = 7,
                )
            )
        )
        val viewModel = SmartCollectionsViewModel(app, dao, mockk(relaxed = true))

        composeTestRule.setContent {
            SmartCollectionsPage(navController = NavHostController(app), viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("7 articles").assertIsDisplayed()
    }

    @Test
    fun page_addButton_showsCreateDialog() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = mockk<SmartCollectionDao>()
        every { dao.observeCollectionsWithCount(any()) } returns MutableStateFlow(emptyList())
        val viewModel = SmartCollectionsViewModel(app, dao, mockk(relaxed = true))

        composeTestRule.setContent {
            SmartCollectionsPage(navController = NavHostController(app), viewModel = viewModel)
        }

        composeTestRule.onNodeWithText("Smart Collections").assertIsDisplayed()
    }
}
