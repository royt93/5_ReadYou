package com.mckimquyen.reader.ui.page.secondbrain

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.galaxy.GalaxyCamera
import com.mckimquyen.reader.domain.model.galaxy.GalaxyGraph
import com.mckimquyen.reader.domain.model.galaxy.GalaxyLayout
import com.mckimquyen.reader.domain.model.galaxy.GalaxyLine
import com.mckimquyen.reader.domain.model.galaxy.GalaxyStar
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_CANVAS_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_EMPTY_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_OPEN_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_RESET_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_SHEET_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_TOGGLE_CONCEPTS_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_TOGGLE_STARS_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GalaxyUiState
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
class GalaxyPageTest {
    private companion object {
        const val DOUBLE_TAP_WAIT_MILLIS = 500L
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private val stars = listOf(
        GalaxyStar("s1", "Chip fab story", "BBC News", 1_000L, setOf("ai")),
        GalaxyStar("s2", "Second AI story", "Wired", 2_000L, setOf("ai")),
        GalaxyStar("s3", "Election night", "Reuters", 3_000L, setOf("politics")),
    )
    private val graph = GalaxyGraph(stars, listOf(GalaxyLine("s1", "s2", 1)))
    private fun ready(selected: String? = null) = GalaxyUiState.Ready(graph, selected, 1_000L, 3_000L, 1_000L, 3_000L)

    private fun show(
        galaxy: GalaxyUiState?,
        onSelectStar: (String?) -> Unit = {},
        onOpen: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) = compose.setContent {
        SecondBrainPage(
            state = SecondBrainUiState.Loading,
            onSelectNode = {},
            onRangeChange = { _, _ -> },
            onOpenArticle = onOpen,
            onRetry = onRetry,
            galaxyState = galaxy,
            onSelectStar = onSelectStar,
            onGalaxyRetry = onRetry,
        )
    }

    private fun openGalaxy() = compose.onNodeWithTag(GALAXY_TOGGLE_STARS_TAG).performClick()

    @Test
    fun withoutGalaxyState_thePageKeepsItsOldBehaviourAndShowsNoToggle() {
        show(galaxy = null)
        compose.onAllNodesWithTag(GALAXY_TOGGLE_STARS_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(GALAXY_CANVAS_TAG).assertCountEquals(0)
        compose.onNodeWithTag(SECOND_BRAIN_TITLE_TAG).assertIsDisplayed()
    }

    @Test
    fun withGalaxyState_opensOnConceptsAndOffersBothModes() {
        show(ready())
        compose.onNodeWithTag(GALAXY_TOGGLE_CONCEPTS_TAG).assertIsDisplayed()
        compose.onNodeWithTag(GALAXY_TOGGLE_STARS_TAG).assertIsDisplayed()
        compose.onAllNodesWithTag(GALAXY_CANVAS_TAG).assertCountEquals(0)
    }

    @Test
    fun switchingToGalaxy_showsTheCanvasAndSwitchingBackHidesIt() {
        show(ready())
        openGalaxy()
        compose.onNodeWithTag(GALAXY_CANVAS_TAG).assertIsDisplayed()

        compose.onNodeWithTag(GALAXY_TOGGLE_CONCEPTS_TAG).performClick()
        compose.onAllNodesWithTag(GALAXY_CANVAS_TAG).assertCountEquals(0)
    }

    @Test
    fun emptyGalaxy_showsThePromptAndNoCanvas() {
        show(GalaxyUiState.Empty)
        openGalaxy()
        compose.onNodeWithTag(GALAXY_EMPTY_TAG).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.galaxy_empty)).assertIsDisplayed()
        compose.onAllNodesWithTag(GALAXY_CANVAS_TAG).assertCountEquals(0)
    }

    @Test
    fun errorGalaxy_offersRetry() {
        var retried = 0
        show(GalaxyUiState.Error, onRetry = { retried++ })
        openGalaxy()
        compose.onNodeWithText(str(R.string.galaxy_error)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.galaxy_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retried) }
    }

    @Test
    fun selectedStar_showsTitleSourceAndDate_andOpenReportsItsId() {
        var opened: String? = null
        show(ready(selected = "s2"), onOpen = { opened = it })
        openGalaxy()

        compose.onNodeWithTag(GALAXY_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("Second AI story").assertIsDisplayed()
        compose.onNodeWithText("Wired", substring = true).assertIsDisplayed()

        compose.onNodeWithTag(GALAXY_OPEN_TAG).performClick()
        compose.runOnIdle { assertEquals("s2", opened) }
    }

    @Test
    fun noSelection_showsNoSheet() {
        show(ready())
        openGalaxy()
        compose.onAllNodesWithTag(GALAXY_SHEET_TAG).assertCountEquals(0)
    }

    @Test
    fun tappingAStarCentre_selectsIt() {
        var selected: String? = "sentinel"
        show(ready(), onSelectStar = { selected = it })
        openGalaxy()
        val positions = GalaxyLayout.compute(stars)
        compose.onNodeWithTag(GALAXY_CANVAS_TAG).performTouchInput {
            val p = GalaxyCamera.DEFAULT.project(positions.getValue("s3"), width.toFloat(), height.toFloat())!!
            click(Offset(p.x, p.y))
        }
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MILLIS) // onTap waits out the double-tap window
        compose.runOnIdle { assertEquals("s3", selected) }
    }

    @Test
    fun tappingEmptySpace_reportsNoSelection() {
        var selected: String? = "sentinel"
        show(ready(selected = "s1"), onSelectStar = { selected = it })
        openGalaxy()
        compose.onNodeWithTag(GALAXY_CANVAS_TAG).performTouchInput { click(Offset(2f, 2f)) }
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MILLIS)
        compose.runOnIdle { assertNull(selected) }
    }

    @Test
    fun resetStartsDisabled_pinchEnablesIt_andResetRestoresTheDefaultView() {
        show(ready())
        openGalaxy()
        compose.onNodeWithTag(GALAXY_RESET_TAG).assertIsNotEnabled()

        compose.onNodeWithTag(GALAXY_CANVAS_TAG).performTouchInput {
            pinch(
                start0 = center + Offset(-40f, 0f), end0 = center + Offset(-200f, 0f),
                start1 = center + Offset(40f, 0f), end1 = center + Offset(200f, 0f),
            )
        }
        compose.onNodeWithTag(GALAXY_RESET_TAG).assertIsEnabled()

        compose.onNodeWithTag(GALAXY_RESET_TAG).performClick()
        compose.onNodeWithTag(GALAXY_RESET_TAG).assertIsNotEnabled()
    }

    @Test
    fun doubleTap_afterAZoom_resetsTheView() {
        show(ready())
        openGalaxy()
        compose.onNodeWithTag(GALAXY_CANVAS_TAG).performTouchInput {
            pinch(
                start0 = center + Offset(-40f, 0f), end0 = center + Offset(-200f, 0f),
                start1 = center + Offset(40f, 0f), end1 = center + Offset(200f, 0f),
            )
        }
        compose.onNodeWithTag(GALAXY_RESET_TAG).assertIsEnabled()
        compose.onNodeWithTag(GALAXY_CANVAS_TAG).performTouchInput { doubleClick(Offset(2f, 2f)) }
        compose.onNodeWithTag(GALAXY_RESET_TAG).assertIsNotEnabled()
    }
}
