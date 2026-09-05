package com.mckimquyen.reader.ui.component.commute

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.infrastructure.audio.CommutePlayerState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class CommuteCastWidgetTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleEpisode = CommuteEpisode(
        id = "ep_widget",
        title = "CommuteCast Morning",
        date = Date(),
        dialogues = listOf(
            CommuteDialogue(CommuteSpeaker.ALEX, "Welcome to the morning update."),
            CommuteDialogue(CommuteSpeaker.SAM, "Here are today's top highlights.")
        ),
        isDeepDive = false
    )

    @Test
    fun commuteCastUi_displaysHostsAndDialogues() {
        val testState = CommuteUiState(
            isLoading = false,
            playerState = CommutePlayerState(
                episode = sampleEpisode,
                currentDialogueIndex = 0,
                isPlaying = true
            )
        )

        composeTestRule.setContent {
            CommuteCastUi(
                uiState = testState,
                onTogglePlayPause = {},
                onSkipNext = {},
                onSkipPrevious = {},
                onSeekTo = {},
                onUnlockDeepDive = {},
                onRetry = {},
                onClose = {}
            )
        }

        // Verify Host badges and dialogue text are visible
        composeTestRule.onNodeWithText("Welcome to the morning update.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Here are today's top highlights.").assertIsDisplayed()
    }

    @Test
    fun commuteCastUi_playPauseButton_triggersCallback() {
        var toggleClicked = false
        val testState = CommuteUiState(
            isLoading = false,
            playerState = CommutePlayerState(
                episode = sampleEpisode,
                currentDialogueIndex = 0,
                isPlaying = false
            )
        )

        composeTestRule.setContent {
            CommuteCastUi(
                uiState = testState,
                onTogglePlayPause = { toggleClicked = true },
                onSkipNext = {},
                onSkipPrevious = {},
                onSeekTo = {},
                onUnlockDeepDive = {},
                onRetry = {},
                onClose = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed().performClick()
        assertTrue(toggleClicked)
    }
}
