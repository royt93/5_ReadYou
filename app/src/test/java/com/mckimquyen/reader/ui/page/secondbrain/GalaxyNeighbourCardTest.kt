package com.mckimquyen.reader.ui.page.secondbrain

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.galaxy.GalaxyLineKind
import com.mckimquyen.reader.domain.model.galaxy.GalaxyNeighbour
import com.mckimquyen.reader.domain.model.galaxy.GalaxyStar
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_NEIGHBOURS_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_NEIGHBOUR_ROW_TAG_PREFIX
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GALAXY_OPEN_TAG
import com.mckimquyen.reader.ui.page.secondbrain.galaxy.GalaxyStarSheet
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
class GalaxyNeighbourCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun str(id: Int, vararg a: Any) = compose.activity.getString(id, *a)

    private val star = GalaxyStar("s1", "Trump and Zelensky at loggerheads", "BBC News", 1_000L, emptySet())
    private fun neighbour(id: String, title: String, vararg terms: String) =
        GalaxyNeighbour(GalaxyStar(id, title, "BBC News", 2_000L, emptySet()), terms.toList(), GalaxyLineKind.TITLE_WORDS)

    private fun show(
        neighbours: List<GalaxyNeighbour>,
        onOpen: (String) -> Unit = {},
        onSelectNeighbour: (String) -> Unit = {},
    ) = compose.setContent {
        GalaxyStarSheet(star = star, onOpen = onOpen, neighbours = neighbours, onSelectNeighbour = onSelectNeighbour)
    }

    @Test fun noNeighboursShowsNoSectionAndNoRows() {
        show(emptyList())
        compose.onAllNodesWithTag(GALAXY_NEIGHBOURS_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(GALAXY_NEIGHBOUR_ROW_TAG_PREFIX + "x").assertCountEquals(0)
        compose.onNodeWithTag(GALAXY_OPEN_TAG).assertIsDisplayed()
    }

    @Test fun showsTheSectionTitleTheOtherTitleAndTheSharedWords() {
        show(listOf(neighbour("s2", "Why is Trump criticising Zelensky", "trump", "zelensky")))
        compose.onNodeWithTag(GALAXY_NEIGHBOURS_TAG).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.galaxy_related_title)).assertIsDisplayed()
        compose.onNodeWithText("Why is Trump criticising Zelensky").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.galaxy_shared_terms, "trump, zelensky")).assertIsDisplayed()
    }

    @Test fun tappingARowSelectsThatNeighbourAndDoesNotOpenAnyArticle() {
        var selected: String? = null
        var opened: String? = null
        show(
            listOf(neighbour("s2", "Second", "alpha", "bravo"), neighbour("s3", "Third", "charlie", "delta")),
            onOpen = { opened = it },
            onSelectNeighbour = { selected = it },
        )
        compose.onNodeWithTag(GALAXY_NEIGHBOUR_ROW_TAG_PREFIX + "s3").performClick()
        compose.runOnIdle {
            assertEquals("s3", selected)
            assertEquals(null, opened)
        }
    }

    @Test fun theOpenButtonStillOpensTheSelectedStarNotTheNeighbour() {
        var opened: String? = null
        show(listOf(neighbour("s2", "Second", "alpha", "bravo")), onOpen = { opened = it })
        compose.onNodeWithTag(GALAXY_OPEN_TAG).performClick()
        compose.runOnIdle { assertEquals("s1", opened) }
    }

    @Test fun threeNeighboursProduceThreeRows() {
        show((1..3).map { neighbour("n$it", "Neighbour $it", "word$it", "term$it") })
        (1..3).forEach { compose.onNodeWithTag(GALAXY_NEIGHBOUR_ROW_TAG_PREFIX + "n$it").assertIsDisplayed() }
    }
}
