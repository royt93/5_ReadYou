package com.mckimquyen.reader.ui.page.home.read.podcaststudio

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PodcastStudioWaveformTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun pausedBarsAreIdenticalNoMatterWhatThePhaseIs() {
        val atStart = waveformBarFractions(phase = 0f, isPlaying = false)
        val later = waveformBarFractions(phase = 0.73f, isPlaying = false)

        assertEquals(WAVEFORM_BAR_COUNT, atStart.size)
        assertEquals(atStart, later)
        assertTrue(atStart.all { it == WAVEFORM_REST_FRACTION })
    }

    @Test fun playingBarsMoveWithThePhase() {
        val first = waveformBarFractions(phase = 0f, isPlaying = true)
        val second = waveformBarFractions(phase = 0.4f, isPlaying = true)

        assertNotEquals(first, second)
    }

    @Test fun playingBarsDifferFromEachOtherAndStayInsideTheDrawableRange() {
        val bars = waveformBarFractions(phase = 0.25f, isPlaying = true)

        assertTrue("bars must not all be equal while playing", bars.distinct().size > 1)
        assertTrue(bars.all { it in WAVEFORM_MIN_FRACTION..1f })
    }

    @Test fun phaseWrapsSoTheLoopHasNoVisibleJump() {
        val start = waveformBarFractions(phase = 0f, isPlaying = true)
        val end = waveformBarFractions(phase = 1f, isPlaying = true)

        start.zip(end).forEach { (a, b) -> assertEquals(a, b, 0.0001f) }
    }

    @Test fun waveformExposesTheDescriptionItWasGiven() {
        compose.setContent {
            PodcastStudioWaveform(
                isPlaying = true,
                speaker = CommuteSpeaker.SAM,
                contentDescription = "Visualizer: playing, Sam speaking",
                modifier = Modifier,
            )
        }

        val node = compose.onNodeWithTag(WAVEFORM_TAG).fetchSemanticsNode()
        assertEquals(
            listOf("Visualizer: playing, Sam speaking"),
            node.config.getOrNull(SemanticsProperties.ContentDescription),
        )
    }

    @Test fun waveformRendersForBothSpeakersAndBothStates() {
        var speaker by mutableStateOf(CommuteSpeaker.ALEX)
        var playing by mutableStateOf(true)
        compose.setContent {
            PodcastStudioWaveform(
                isPlaying = playing,
                speaker = speaker,
                contentDescription = "desc",
                modifier = Modifier,
            )
        }
        compose.onNodeWithTag(WAVEFORM_TAG).assertExists()

        compose.runOnIdle { speaker = CommuteSpeaker.SAM }
        compose.onNodeWithTag(WAVEFORM_TAG).assertExists()

        compose.runOnIdle { playing = false }
        compose.onNodeWithTag(WAVEFORM_TAG).assertExists()
    }
}
