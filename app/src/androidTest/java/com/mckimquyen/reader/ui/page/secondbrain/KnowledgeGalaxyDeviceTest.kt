package com.mckimquyen.reader.ui.page.secondbrain

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.mckimquyen.reader.domain.model.galaxy.GalaxyCamera
import com.mckimquyen.reader.domain.model.galaxy.GalaxyGraph
import com.mckimquyen.reader.domain.model.galaxy.GalaxyLayout
import com.mckimquyen.reader.domain.model.galaxy.GalaxyLine
import com.mckimquyen.reader.domain.model.galaxy.GalaxyStar
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_CANVAS_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_OPEN_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_RESET_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_SHEET_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_TOGGLE_STARS_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GalaxyUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Runs the real Compose galaxy on a device: no database, no network, nothing from the user's data. */
class KnowledgeGalaxyDeviceTest {
    private companion object {
        const val DOUBLE_TAP_WAIT_MILLIS = 500L
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val stars = listOf(
        GalaxyStar("s1", "Chip fab story", "BBC News", 1_000L, setOf("ai")),
        GalaxyStar("s2", "Second AI story", "Wired", 2_000L, setOf("ai")),
        GalaxyStar("s3", "Election night", "Reuters", 3_000L, setOf("politics")),
    )

    @Test
    fun galaxy_rotatesSelectsAStarAndOpensItsArticle() {
        var selected: String? = null
        var opened: String? = null
        composeRule.setContent {
            val graph = GalaxyGraph(stars, listOf(GalaxyLine("s1", "s2", 1)))
            SecondBrainPage(
                state = SecondBrainUiState.Loading,
                onSelectNode = {},
                onRangeChange = { _, _ -> },
                onOpenArticle = { opened = it },
                onRetry = {},
                galaxyState = GalaxyUiState.Ready(graph, selected, 1_000L, 3_000L, 1_000L, 3_000L),
                onSelectStar = { selected = it },
            )
        }
        composeRule.onNodeWithTag(GALAXY_TOGGLE_STARS_TAG).performClick()
        composeRule.onNodeWithTag(GALAXY_CANVAS_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(GALAXY_RESET_TAG).assertIsNotEnabled()

        composeRule.onNodeWithTag(GALAXY_CANVAS_TAG).performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(GALAXY_RESET_TAG).assertIsEnabled()
        composeRule.onNodeWithTag(GALAXY_RESET_TAG).performClick()
        composeRule.onNodeWithTag(GALAXY_RESET_TAG).assertIsNotEnabled()

        val positions = GalaxyLayout.compute(stars)
        composeRule.onNodeWithTag(GALAXY_CANVAS_TAG).performTouchInput {
            val p = GalaxyCamera.DEFAULT.project(positions.getValue("s2"), width.toFloat(), height.toFloat())!!
            click(Offset(p.x, p.y))
        }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MILLIS)
        composeRule.runOnIdle { assertEquals("s2", selected) }
    }

    @Test
    fun selectedStar_showsItsDetailsAndOpenReportsTheId() {
        var opened: String? = null
        composeRule.setContent {
            val graph = GalaxyGraph(stars, emptyList())
            SecondBrainPage(
                state = SecondBrainUiState.Loading,
                onSelectNode = {},
                onRangeChange = { _, _ -> },
                onOpenArticle = { opened = it },
                onRetry = {},
                galaxyState = GalaxyUiState.Ready(graph, "s3", 1_000L, 3_000L, 1_000L, 3_000L),
            )
        }
        composeRule.onNodeWithTag(GALAXY_TOGGLE_STARS_TAG).performClick()
        composeRule.onNodeWithTag(GALAXY_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Election night").assertIsDisplayed()
        composeRule.onNodeWithTag(GALAXY_OPEN_TAG).performClick()
        composeRule.runOnIdle { assertEquals("s3", opened) }
    }
}
