package com.mckimquyen.reader.ui.page.secondbrain

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.secondbrain.BrainArticleInfo
import com.mckimquyen.reader.domain.model.secondbrain.BrainEdge
import com.mckimquyen.reader.domain.model.secondbrain.BrainGraph
import com.mckimquyen.reader.domain.model.secondbrain.BrainNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SecondBrainPageWidgetTest {
    private companion object {
        const val DOUBLE_TAP_WAIT_MILLIS = 500L
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int, vararg args: Any) = composeRule.activity.getString(id, *args)

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

    private fun show(
        state: SecondBrainUiState,
        onSelect: (String?) -> Unit = {},
        onRange: (Long, Long) -> Unit = { _, _ -> },
        onOpen: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) = composeRule.setContent {
        SecondBrainPage(state, onSelect, onRange, onOpen, onRetry)
    }

    @Test
    fun empty_showsGuidance() {
        show(SecondBrainUiState.Empty)
        composeRule.onNodeWithText(str(R.string.second_brain_empty)).assertIsDisplayed()
    }

    @Test
    fun error_showsMessage_andRetryInvokesCallback() {
        var retries = 0
        show(SecondBrainUiState.Error, onRetry = { retries++ })

        composeRule.onNodeWithText(str(R.string.second_brain_error)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.second_brain_retry)).performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun ready_exposesGraphSummaryToAccessibility() {
        show(SecondBrainUiState.Ready(graph, null, 1_000L, 2_000L))
        composeRule.onNodeWithContentDescription(str(R.string.second_brain_graph_desc, 2, 1)).assertIsDisplayed()
    }

    @Test
    fun ready_withoutSelection_hidesRelatedArticles() {
        show(SecondBrainUiState.Ready(graph, null, 1_000L, 2_000L))
        composeRule.onNodeWithText("First AI story").assertDoesNotExist()
    }

    @Test
    fun ready_withSelection_listsRelatedArticlesNewestFirst_andOpensOne() {
        var opened: String? = null
        show(SecondBrainUiState.Ready(graph, "ai", 1_000L, 2_000L), onOpen = { opened = it })

        composeRule.onNodeWithText(str(R.string.second_brain_node_articles, 2)).assertIsDisplayed()
        composeRule.onNodeWithText("Chip fab story").assertIsDisplayed()
        composeRule.onNodeWithText("First AI story").performClick()
        composeRule.runOnIdle { assertEquals("a1", opened) }
    }

    @Test
    fun tappingEmptyCanvasArea_reportsNoSelection() {
        var selected: String? = "sentinel"
        show(SecondBrainUiState.Ready(graph, "ai", 1_000L, 2_000L), onSelect = { selected = it })

        // Top-left corner of the canvas is far from every node (layout keeps nodes near the centre).
        composeRule.onNodeWithContentDescription(str(R.string.second_brain_graph_desc, 2, 1))
            .performTouchInput { click(Offset(2f, 2f)) }
        // onTap waits out the double-tap window before firing
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MILLIS)

        composeRule.runOnIdle { assertNull(selected) }
    }

    @Test
    fun tappingNodeCentre_selectsThatNode() {
        var selected: String? = null
        show(SecondBrainUiState.Ready(graph, null, 1_000L, 2_000L), onSelect = { selected = it })
        val positions = BrainGraphLayout.compute(graph)
        val heaviest = positions.getValue("ai")

        composeRule.onNodeWithContentDescription(str(R.string.second_brain_graph_desc, 2, 1))
            .performTouchInput { click(Offset(heaviest.x * width, heaviest.y * height)) }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MILLIS)

        composeRule.runOnIdle { assertEquals("ai", selected) }
    }

    @Test
    fun singleInstantGraph_hidesTimeSlider() {
        val flat = graph.copy(minTime = 1_000L, maxTime = 1_000L)
        show(SecondBrainUiState.Ready(flat, null, 1_000L, 1_000L))
        composeRule.onNodeWithText(str(R.string.second_brain_range_label)).assertDoesNotExist()
    }

    @Test
    fun wideTimeRange_showsSlider() {
        show(SecondBrainUiState.Ready(graph, null, 1_000L, 2_000L))
        composeRule.onNodeWithText(str(R.string.second_brain_range_label)).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(graph.maxTime > graph.minTime) }
    }

    @Test
    fun readyWithNoConceptInTheWindow_showsAHint_andKeepsTheRangeSliderToWidenIt() {
        val quiet = BrainGraph(emptyList(), emptyList(), 1_000L, 2_000L)
        show(SecondBrainUiState.Ready(quiet, null, 1_200L, 1_300L))

        composeRule.onNodeWithText(str(R.string.second_brain_empty_window)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.second_brain_range_label)).assertIsDisplayed()
    }

    private fun zoomIs(percent: Int) = SemanticsMatcher("zoom is $percent%") {
        it.config.getOrNull(SemanticsProperties.StateDescription) == str(R.string.second_brain_zoom_level, percent)
    }

    @Test
    fun realPinch_zoomsIn_andDoubleTapRestoresHundredPercent() {
        show(SecondBrainUiState.Ready(graph, null, 1_000L, 2_000L))
        val graphNode = composeRule.onNodeWithTag(SECOND_BRAIN_GRAPH_TAG)
        graphNode.assert(zoomIs(100))
        composeRule.onNodeWithTag(SECOND_BRAIN_RESET_TAG).assertIsNotEnabled()

        graphNode.performTouchInput {
            pinch(
                start0 = center + Offset(-40f, 0f), end0 = center + Offset(-200f, 0f),
                start1 = center + Offset(40f, 0f), end1 = center + Offset(200f, 0f),
            )
        }
        composeRule.onNodeWithTag(SECOND_BRAIN_RESET_TAG).assertIsEnabled()

        graphNode.performTouchInput { doubleClick(Offset(2f, 2f)) }
        graphNode.assert(zoomIs(100))
        composeRule.onNodeWithTag(SECOND_BRAIN_RESET_TAG).assertIsNotEnabled()
    }
}
