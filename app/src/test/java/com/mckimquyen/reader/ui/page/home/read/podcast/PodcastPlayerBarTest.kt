package com.mckimquyen.reader.ui.page.home.read.podcast

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState
import com.mckimquyen.reader.domain.model.podcast.PodcastStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PodcastPlayerBarTest {

    @get:Rule
    val rule = createComposeRule()

    private val paused = PodcastPlaybackState(articleId = "a1", status = PodcastStatus.PAUSED, durationSec = 600)

    private class Calls {
        var toggles = 0
        val skips = mutableListOf<Int>()
        val seeks = mutableListOf<Int>()
        val speeds = mutableListOf<Float>()
        val sleeps = mutableListOf<Int?>()
        var retries = 0
    }

    private fun show(state: PodcastPlaybackState, calls: Calls = Calls()): Calls {
        rule.setContent {
            PodcastPlayerBar(
                state = state,
                onToggle = { calls.toggles++ },
                onSkip = { calls.skips += it },
                onSeek = { calls.seeks += it },
                onSpeed = { calls.speeds += it },
                onSleep = { calls.sleeps += it },
                onRetry = { calls.retries++ },
            )
        }
        return calls
    }

    @Test fun formatPlaybackTime_formats() {
        assertEquals("0:00", formatPlaybackTime(0))
        assertEquals("1:05", formatPlaybackTime(65))
        assertEquals("1:00:00", formatPlaybackTime(3600))
        assertEquals("1:02:03", formatPlaybackTime(3723))
        assertEquals("0:00", formatPlaybackTime(-3))
    }

    @Test fun paused_showsPlayAndTimes() {
        show(paused)
        rule.onNodeWithTag(PODCAST_BAR_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription("Play").assertIsDisplayed()
        rule.onNodeWithText("0:00").assertIsDisplayed()
        rule.onNodeWithText("10:00").assertIsDisplayed()
    }

    @Test fun playing_showsPause() {
        show(paused.copy(status = PodcastStatus.PLAYING))
        rule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test fun controls_callIntents() {
        val calls = show(paused)
        rule.onNodeWithTag(PODCAST_TOGGLE_TAG).performClick()
        rule.onNodeWithTag(PODCAST_BACK_TAG).performClick()
        rule.onNodeWithTag(PODCAST_FORWARD_TAG).performClick()
        assertEquals(1, calls.toggles)
        assertEquals(listOf(-15, 15), calls.skips)
    }

    @Test fun speedChip_showsCurrentAndPicksSpeed() {
        val calls = show(paused)
        rule.onNodeWithText("1.0×").assertIsDisplayed()
        rule.onNodeWithTag(PODCAST_SPEED_TAG).performClick()
        listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { rule.onNodeWithTag(PODCAST_SPEED_ITEM_TAG_PREFIX + it).assertIsDisplayed() }
        rule.onNodeWithTag(PODCAST_SPEED_ITEM_TAG_PREFIX + 1.5f).performClick()
        assertEquals(listOf(1.5f), calls.speeds)
    }

    @Test fun sleepButton_offThenPicksTimerAndOff() {
        val calls = show(paused)
        rule.onNodeWithTag(PODCAST_SLEEP_TAG).performClick()
        listOf(15, 30, 45).forEach { rule.onNodeWithTag(PODCAST_SLEEP_ITEM_TAG_PREFIX + it).assertIsDisplayed() }
        rule.onNodeWithTag(PODCAST_SLEEP_ITEM_TAG_PREFIX + 30).performClick()
        rule.onNodeWithTag(PODCAST_SLEEP_TAG).performClick()
        rule.onNodeWithTag(PODCAST_SLEEP_OFF_ITEM_TAG).performClick()
        assertEquals(listOf(30, null), calls.sleeps)
    }

    @Test fun sleepButton_showsRemainingTime() {
        show(paused.copy(sleepRemainingSec = 899))
        rule.onNodeWithText("14:59").assertIsDisplayed()
    }

    @Test fun error_showsMessageAndRetry() {
        val calls = show(paused.copy(status = PodcastStatus.ERROR))
        rule.onNodeWithTag(PODCAST_ERROR_TAG).assertIsDisplayed()
        rule.onNodeWithText("Could not play this episode.").assertIsDisplayed()
        rule.onNodeWithTag(PODCAST_RETRY_TAG).performClick()
        assertEquals(1, calls.retries)
    }

    @Test fun noErrorRow_whenNotError() {
        show(paused)
        rule.onNodeWithTag(PODCAST_ERROR_TAG).assertDoesNotExist()
    }

    @Test fun slider_commitsSeekWithinDuration() {
        val calls = show(paused)
        rule.onNodeWithTag(PODCAST_SEEK_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(300f) }
        rule.waitForIdle()
        assertEquals(1, calls.seeks.size)
        assertTrue(calls.seeks.single() in 0..600)
    }

    @Test fun interactiveNodes_haveDescriptions() {
        show(paused)
        listOf("Play", "Back 15 seconds", "Forward 15 seconds", "Playback speed", "Sleep timer", "Playback position").forEach {
            rule.onNode(hasContentDescription(it)).assertExists()
        }
    }
}
