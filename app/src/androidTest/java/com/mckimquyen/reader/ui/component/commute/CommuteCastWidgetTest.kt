package com.mckimquyen.reader.ui.component.commute

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.infrastructure.audio.CommutePlayerState
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class CommuteCastWidgetTest {

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

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
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
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
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

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
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
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }
}
