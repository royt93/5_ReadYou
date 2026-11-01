package com.mckimquyen.reader.ui.page.home.read.steelman

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.steelman.ContrastingArticle
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ContrastingCoverageDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = ContrastingCoverageState.Ready(
        phrases = listOf("Starlink", "SpaceX"),
        selectedPhrase = "Starlink",
        items = listOf(
            ContrastingArticle("a1", "Scientists warn Starlink disrupts research", "Science Wire", Date(1_000L), "warn"),
        ),
    )

    @Test
    fun card_rendersOnRealRenderer_andChipAndRowFireCallbacks() {
        var chosenPhrase = ""
        var openedId = ""
        compose.setContent {
            ContrastingCoverageCard(
                state = state,
                onSelectPhrase = { chosenPhrase = it },
                onOpenArticle = { openedId = it },
            )
        }

        compose.onNodeWithTag(ECHO_CONTRAST_CARD_TAG).assertIsDisplayed()
        compose.onNodeWithTag("${ECHO_CONTRAST_CHIP_PREFIX}SpaceX").performClick()
        compose.onNodeWithTag("${ECHO_CONTRAST_ROW_PREFIX}a1").performClick()

        assertEquals("SpaceX", chosenPhrase)
        assertEquals("a1", openedId)
    }
}
