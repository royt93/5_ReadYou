package com.mckimquyen.reader.ui.page.home.read.steelman

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.steelman.ContrastingArticle
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContrastingCoverageCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val readyState = ContrastingCoverageState.Ready(
        phrases = listOf("Starlink", "SpaceX"),
        selectedPhrase = "Starlink",
        items = listOf(
            ContrastingArticle("a1", "Astronomers warn Starlink disrupts night sky", "ScienceFeed", Date(1000L), "warn"),
            ContrastingArticle("a2", "Regulator disputes satellite coverage claim", "Wired", Date(2000L), "disputes"),
        ),
    )

    @Test
    fun card_displaysTitle_chips_articles_andLimitation() {
        compose.setContent {
            ContrastingCoverageCard(
                state = readyState,
                onSelectPhrase = {},
                onOpenArticle = {},
            )
        }

        compose.onNodeWithTag(ECHO_CONTRAST_CARD_TAG).assertIsDisplayed()
        compose.onNodeWithText("Astronomers warn Starlink disrupts night sky", substring = true).assertIsDisplayed()
        compose.onNodeWithText("ScienceFeed", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Not a fact-check", substring = true).assertIsDisplayed()
    }

    @Test
    fun clickingChip_firesPhraseCallback() {
        var selected = ""
        compose.setContent {
            ContrastingCoverageCard(
                state = readyState,
                onSelectPhrase = { selected = it },
                onOpenArticle = {},
            )
        }

        compose.onNodeWithTag("${ECHO_CONTRAST_CHIP_PREFIX}SpaceX").performClick()
        assertEquals("SpaceX", selected)
    }

    @Test
    fun clickingArticleRow_firesOpenCallback() {
        var opened = ""
        compose.setContent {
            ContrastingCoverageCard(
                state = readyState,
                onSelectPhrase = {},
                onOpenArticle = { opened = it },
            )
        }

        compose.onNodeWithTag("${ECHO_CONTRAST_ROW_PREFIX}a1").performClick()
        assertEquals("a1", opened)
    }

    @Test
    fun card_notDrawn_whenStateIsNotReady() {
        compose.setContent {
            ContrastingCoverageSection(
                state = ContrastingCoverageState.None,
                enabled = true,
                onSelectPhrase = {},
                onOpenArticle = {},
            )
        }
        compose.onAllNodesWithTag(ECHO_CONTRAST_CARD_TAG).assertCountEquals(0)
    }

    @Test
    fun card_notDrawn_whenDisabled() {
        compose.setContent {
            ContrastingCoverageSection(
                state = readyState,
                enabled = false,
                onSelectPhrase = {},
                onOpenArticle = {},
            )
        }
        compose.onAllNodesWithTag(ECHO_CONTRAST_CARD_TAG).assertCountEquals(0)
    }
}
