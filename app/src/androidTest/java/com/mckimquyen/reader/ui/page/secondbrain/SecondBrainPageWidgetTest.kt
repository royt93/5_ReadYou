package com.mckimquyen.reader.ui.page.secondbrain

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
}
