package com.mckimquyen.reader.ui.page.home.read.podcaststudio

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.domain.model.podcast.PodcastScriptSource
import com.mckimquyen.reader.infrastructure.audio.CommutePlayerState
import com.mckimquyen.reader.infrastructure.audio.CommuteVoiceMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PodcastStudioSheetTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val article = Article(
        id = "a1", date = Date(0), title = "Space launch", rawDescription = "raw", shortDescription = "short",
        link = "https://x.test", feedId = "f", accountId = 1,
    )
    private val episode = CommuteEpisode(
        id = "e1", title = "Space launch",
        dialogues = listOf(
            CommuteDialogue(CommuteSpeaker.ALEX, "A rocket reached orbit."),
            CommuteDialogue(CommuteSpeaker.SAM, "A notable milestone."),
        ),
    )

    private var dismissed = 0
    private val consents = mutableListOf<Boolean>()
    private var conflictConfirmed = 0
    private var conflictDismissed = 0
    private var toggles = 0
    private var nexts = 0
    private var previouses = 0
    private val seeks = mutableListOf<Int>()
    private var retries = 0

    private fun show(state: PodcastStudioUiState) = compose.setContent {
        PodcastStudioSheet(
            state = state,
            onDismiss = { dismissed++ },
            onConsent = { consents += it },
            onConfirmConflict = { conflictConfirmed++ },
            onDismissConflict = { conflictDismissed++ },
            onToggle = { toggles++ },
            onSkipNext = { nexts++ },
            onSkipPrevious = { previouses++ },
            onSeek = { seeks += it },
            onRetry = { retries++ },
        )
    }

    private fun ready(
        source: PodcastScriptSource = PodcastScriptSource.GEMINI,
        voiceMode: CommuteVoiceMode = CommuteVoiceMode.REAL_DUAL,
        index: Int = 0,
        playing: Boolean = true,
    ) = PodcastStudioUiState.Ready(
        episode, source,
        CommutePlayerState(episode = episode, currentDialogueIndex = index, isPlaying = playing, voiceMode = voiceMode),
    )

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    @Test fun idleShowsNothing() {
        show(PodcastStudioUiState.Idle)
        compose.onNodeWithTag(PODCAST_STUDIO_SHEET_TAG).assertDoesNotExist()
    }

    @Test fun consentDialogShowsItsMessageAndReportsBothChoices() {
        show(PodcastStudioUiState.AwaitingConsent(article))
        compose.onNodeWithTag(PODCAST_CONSENT_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.podcast_studio_consent_message)).assertIsDisplayed()

        compose.onNodeWithText(str(R.string.podcast_studio_consent_decline)).performClick()
        compose.onNodeWithText(str(R.string.podcast_studio_consent_agree)).performClick()

        assertEquals(listOf(false, true), consents)
    }

    @Test fun conflictDialogReportsConfirmAndCancel() {
        show(PodcastStudioUiState.ConfirmConflict(article))
        compose.onNodeWithTag(PODCAST_CONFLICT_DIALOG_TAG).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.podcast_studio_conflict_message)).assertIsDisplayed()

        compose.onNodeWithText(str(R.string.cancel)).performClick()
        compose.onNodeWithText(str(R.string.podcast_studio_conflict_title)).assertIsDisplayed()
        assertEquals(1, conflictDismissed)
        assertEquals(0, conflictConfirmed)
    }

    @Test fun confirmingTheConflictDialogIsReported() {
        show(PodcastStudioUiState.ConfirmConflict(article))
        compose.onNodeWithTag(PODCAST_CONFLICT_CONFIRM_TAG).performClick()
        assertEquals(1, conflictConfirmed)
    }

    @Test fun generatingShowsProgress() {
        show(PodcastStudioUiState.Generating)
        compose.onNodeWithTag(PODCAST_PROGRESS_TAG).assertIsDisplayed()
    }

    @Test fun errorShowsTheErrorAndRetries() {
        show(PodcastStudioUiState.Error)
        compose.onNodeWithText(str(R.string.feed_autopilot_error)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.retry)).performClick()
        assertEquals(1, retries)
    }

    @Test fun readySheetListsEveryLineAndSeeksWhenALineIsTapped() {
        show(ready())
        compose.onNodeWithTag(PODCAST_STUDIO_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("A rocket reached orbit.").assertIsDisplayed()
        compose.onNodeWithText("A notable milestone.").assertIsDisplayed()

        compose.onNodeWithText("A notable milestone.").performClick()
        assertEquals(listOf(1), seeks)
    }

    @Test fun voiceBadgeIsHonestAboutTwoVoicesVersusOne() {
        show(ready(voiceMode = CommuteVoiceMode.REAL_DUAL))
        compose.onNodeWithText(str(R.string.commute_voice_mode_dual)).assertIsDisplayed()
    }

    @Test fun voiceBadgeAdmitsWhenOneVoiceIsShifted() {
        show(ready(voiceMode = CommuteVoiceMode.SIMULATED))
        compose.onNodeWithText(str(R.string.commute_voice_mode_simulated)).assertIsDisplayed()
    }

    @Test fun scriptBadgeSaysAiWhenGeminiWroteIt() {
        show(ready(source = PodcastScriptSource.GEMINI))
        compose.onNodeWithText(str(R.string.podcast_studio_badge_source_gemini)).assertIsDisplayed()
    }

    @Test fun scriptBadgeSaysOfflineWhenNoAiWasUsed() {
        show(ready(source = PodcastScriptSource.HEURISTIC))
        compose.onNodeWithText(str(R.string.podcast_studio_badge_source_heuristic)).assertIsDisplayed()
    }

    @Test fun controlsReportTheirTaps() {
        show(ready(playing = true))
        compose.onNodeWithContentDescription(str(R.string.podcast_studio_pause)).performClick()
        compose.onNodeWithContentDescription(str(R.string.podcast_studio_next)).performClick()
        compose.onNodeWithContentDescription(str(R.string.podcast_studio_previous)).performClick()

        assertEquals(1, toggles)
        assertEquals(1, nexts)
        assertEquals(1, previouses)
    }

    @Test fun playButtonSaysPlayWhenPaused() {
        show(ready(playing = false))
        compose.onNodeWithContentDescription(str(R.string.podcast_studio_play)).assertIsDisplayed()
    }

    @Test fun closeButtonDismisses() {
        show(ready())
        compose.onNodeWithContentDescription(str(R.string.close)).performClick()
        assertEquals(1, dismissed)
    }

    @Test fun waveformDescriptionNamesTheStateAndTheSpeaker() {
        show(ready(index = 1, playing = true))
        compose.onNodeWithContentDescription(str(R.string.podcast_studio_waveform_playing, str(R.string.podcast_studio_host_sam)))
            .assertExists()
    }

    @Test fun waveformDescriptionSaysPausedWhenPaused() {
        show(ready(index = 0, playing = false))
        compose.onNodeWithContentDescription(str(R.string.podcast_studio_waveform_paused)).assertExists()
    }
}
