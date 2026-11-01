package com.mckimquyen.reader.ui.page.secondbrain

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.secondbrain.BrainArticleInfo
import com.mckimquyen.reader.domain.model.secondbrain.BrainEdge
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraph
import com.mckimquyen.reader.domain.model.secondbrain.BrainNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SecondBrainViewportUiTest {
    private companion object {
        const val DOUBLE_TAP_WAIT_MILLIS = 500L
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private val graph = BrainGraph(
        nodes = listOf(
            BrainNode("ai", "Ai", 2, listOf("a1", "a2"), 1_000L, 2_000L),
            BrainNode("chips", "Chips", 1, listOf("a2"), 2_000L, 2_000L),
        ),
        edges = listOf(BrainEdge("ai", "chips", 1)),
        minTime = 1_000L,
        maxTime = 2_000L,
        articleInfo = mapOf(
            "a1" to BrainArticleInfo("a1", "First AI story", 1_000L),
            "a2" to BrainArticleInfo("a2", "Chip fab story", 2_000L),
        ),
    )

    private fun show(selected: String? = null, onSelect: (String?) -> Unit = {}) = compose.setContent {
        SecondBrainPage(
            state = SecondBrainUiState.Ready(graph, selected, 1_000L, 2_000L),
            onSelectNode = onSelect,
            onRangeChange = { _, _ -> },
            onOpenArticle = {},
            onRetry = {},
        )
    }

    private fun zoomIs(percent: Int) = SemanticsMatcher("zoom is $percent%") {
        it.config.getOrNull(SemanticsProperties.StateDescription) == str(R.string.second_brain_zoom_level, percent)
    }

    private fun pinchOut() = compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).performTouchInput {
        pinch(
            start0 = center + Offset(-40f, 0f), end0 = center + Offset(-200f, 0f),
            start1 = center + Offset(40f, 0f), end1 = center + Offset(200f, 0f),
        )
    }

    @Test
    fun withoutSelection_showsHintAndHidesArticles() {
        show()
        compose.onNodeWithTag(SECOND_BRAIN_LIST_TAG).assertExists()
        compose.onNodeWithTag(SECOND_BRAIN_HINT_TAG).assertIsDisplayed()
        compose.onAllNodesWithText("First AI story").assertCountEquals(0)
    }

    @Test
    fun withSelection_hidesHintAndListsRelatedArticles() {
        show(selected = "ai")
        compose.onAllNodesWithTag(SECOND_BRAIN_HINT_TAG).assertCountEquals(0)
        compose.onNodeWithText("Chip fab story").assertIsDisplayed()
        compose.onNodeWithText("First AI story").assertIsDisplayed()
    }

    @Test
    fun startsAtHundredPercent_withResetDisabled_andShowsTheGestureCaption() {
        show()
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).assert(zoomIs(100))
        compose.onNodeWithTag(SECOND_BRAIN_RESET_TAG).assertIsNotEnabled()
        compose.onNodeWithText(str(R.string.second_brain_zoom_hint)).assertIsDisplayed()
    }

    @Test
    fun pinchOut_zoomsIn_andResetRestoresIdentity() {
        show()
        pinchOut()
        compose.onNodeWithTag(SECOND_BRAIN_RESET_TAG).assertIsEnabled()

        compose.onNodeWithTag(SECOND_BRAIN_RESET_TAG).performClick()
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).assert(zoomIs(100))
        compose.onNodeWithTag(SECOND_BRAIN_RESET_TAG).assertIsNotEnabled()
    }

    @Test
    fun doubleTap_afterZoom_resetsTheViewport() {
        show()
        pinchOut()
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).performTouchInput { doubleClick(Offset(2f, 2f)) }
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).assert(zoomIs(100))
    }

    @Test
    fun tappingTheHeaviestNodeCentre_selectsIt() {
        var selected: String? = null
        show(onSelect = { selected = it })
        val centre = BrainGraphLayout.compute(graph).getValue("ai")
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).performTouchInput { click(Offset(centre.x * width, centre.y * height)) }
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MILLIS) // onTap waits out the double-tap window
        compose.runOnIdle { assertEquals("ai", selected) }
    }

    @Test
    fun tappingAnEmptyCorner_reportsNoSelection() {
        var selected: String? = "sentinel"
        show(selected = "ai", onSelect = { selected = it })
        compose.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG).performTouchInput { click(Offset(2f, 2f)) }
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MILLIS) // onTap waits out the double-tap window
        compose.runOnIdle { assertNull(selected) }
    }
}
