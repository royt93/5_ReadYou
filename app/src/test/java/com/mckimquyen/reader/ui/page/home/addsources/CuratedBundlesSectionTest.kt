package com.mckimquyen.reader.ui.page.home.addsources

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.curated.BundleSubscribeResult
import com.mckimquyen.reader.domain.curated.CuratedBundle
import com.mckimquyen.reader.domain.curated.CuratedSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CuratedBundlesSectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val tech = CuratedBundle(
        "tech_ai", "Technology & AI", "Công nghệ & AI", "2026-11-02",
        listOf(CuratedSource("TechCrunch", "https://techcrunch.com/feed/"), CuratedSource("The Verge", "https://www.theverge.com/rss/index.xml")),
    )
    private val design = CuratedBundle(
        "design", "Design & Creativity", "Thiết kế & Sáng tạo", "2026-11-02",
        listOf(CuratedSource("Smashing Magazine", "https://www.smashingmagazine.com/feed/")),
    )

    private fun show(
        state: AddSourcesUiState,
        language: String = "en",
        onSubscribe: (CuratedBundle) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) = compose.setContent {
        CuratedBundlesSection(state = state, language = language, onSubscribe = onSubscribe, onDismissResult = onDismiss)
    }

    private fun state(vararg changes: (AddSourcesUiState) -> AddSourcesUiState): AddSourcesUiState =
        changes.fold(AddSourcesUiState(bundles = listOf(tech, design), canSubscribeBundles = true)) { acc, f -> f(acc) }

    @Test
    fun showsEveryBundleWithItsCountAndCheckedDate() {
        show(state())

        compose.onNodeWithText("Technology & AI").assertIsDisplayed()
        compose.onNodeWithText("Design & Creativity").assertIsDisplayed()
        compose.onNodeWithText("Sources: 2", substring = true).assertIsDisplayed()
        compose.onAllNodesWithTag(CURATED_BUNDLE_VERIFIED_TAG_PREFIX + "tech_ai", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("2026-11-02", substring = true, useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun usesTheVietnameseTitleForVietnameseUi() {
        show(state(), language = "vi")

        compose.onNodeWithText("Công nghệ & AI").assertIsDisplayed()
        compose.onNodeWithText("Thiết kế & Sáng tạo").assertIsDisplayed()
    }

    @Test
    fun expandingABundleShowsOnlyItsSourceNames() {
        show(state())

        compose.onNodeWithTag(CURATED_BUNDLE_HEADER_TAG_PREFIX + "tech_ai").performClick()

        compose.onNodeWithText("TechCrunch").assertIsDisplayed()
        compose.onNodeWithText("The Verge").assertIsDisplayed()
        compose.onAllNodesWithTag(CURATED_BUNDLE_SOURCE_TAG_PREFIX + "design").assertCountEquals(0)
    }

    @Test
    fun subscribeButton_callsBackWithThatBundle() {
        var subscribed: CuratedBundle? = null
        show(state(), onSubscribe = { subscribed = it })

        compose.onNodeWithTag(CURATED_BUNDLE_SUBSCRIBE_TAG_PREFIX + "design").performClick()

        assertEquals("design", subscribed?.id)
    }

    @Test
    fun nonLocalAccount_hidesEverySubscribeButtonAndExplainsWhy() {
        show(state({ it.copy(canSubscribeBundles = false) }))

        compose.onAllNodesWithTag(CURATED_BUNDLE_SUBSCRIBE_TAG_PREFIX + "tech_ai").assertCountEquals(0)
        compose.onAllNodesWithTag(CURATED_BUNDLE_SUBSCRIBE_TAG_PREFIX + "design").assertCountEquals(0)
        compose.onNodeWithTag(CURATED_BUNDLE_LOCAL_ONLY_TAG).assertIsDisplayed()
    }

    @Test
    fun whileOneBundleSubscribes_itsButtonIsDisabledAndShowsProgress() {
        show(state({ it.copy(subscribingBundleId = "tech_ai", bundleProgress = 1 to 2) }))

        compose.onNodeWithTag(CURATED_BUNDLE_SUBSCRIBE_TAG_PREFIX + "tech_ai").assertIsNotEnabled()
        compose.onNodeWithText("Subscribing 1 of 2", substring = true).assertIsDisplayed()
    }

    @Test
    fun showsTheResultCounts_andDismissCallsBack() {
        var dismissed = 0
        show(
            state({ it.copy(bundleResult = BundleSubscribeResult(added = 2, skippedExisting = 1, failed = 3), bundleResultId = "tech_ai") }),
            onDismiss = { dismissed++ },
        )

        compose.onNodeWithText("Added 2", substring = true).assertIsDisplayed()
        compose.onNodeWithText("already had 1", substring = true).assertIsDisplayed()
        compose.onNodeWithText("failed 3", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(CURATED_BUNDLE_DISMISS_TAG).performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun aResultWithNothingAdded_explainsNoGroupWasCreated() {
        show(state({ it.copy(bundleResult = BundleSubscribeResult(0, 0, 2), bundleResultId = "tech_ai") }))

        compose.onNodeWithText("no group was created", substring = true).assertIsDisplayed()
    }

    @Test
    fun anError_isShownWithItsMessage() {
        show(state({ it.copy(bundleError = "offline") }))

        compose.onNodeWithText("Could not subscribe: offline").assertIsDisplayed()
    }

    @Test
    fun withoutBundles_nothingIsDrawn() {
        show(AddSourcesUiState())

        compose.onAllNodesWithTag(CURATED_BUNDLES_SECTION_TAG).assertCountEquals(0)
    }
}
