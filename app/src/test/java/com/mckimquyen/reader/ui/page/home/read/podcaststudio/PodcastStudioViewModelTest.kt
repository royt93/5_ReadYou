package com.mckimquyen.reader.ui.page.home.read.podcaststudio

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.domain.model.podcast.PodcastScriptSource
import com.mckimquyen.reader.domain.model.podcast.PodcastStudioEpisode
import com.mckimquyen.reader.domain.sv.PodcastStudioScriptService
import com.mckimquyen.reader.infrastructure.audio.CommuteAudioPlayer
import com.mckimquyen.reader.infrastructure.audio.CommutePlayerState
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.podcastStudioGeminiConsent
import com.mckimquyen.reader.ui.ext.putBlocking
import com.mckimquyen.reader.ui.ext.setPodcastStudioGeminiConsent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PodcastStudioViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val context: Context = application
    private val scriptService: PodcastStudioScriptService = mockk()
    private val audioPlayer: CommuteAudioPlayer = mockk(relaxed = true)
    private val playerState = MutableStateFlow(CommutePlayerState())
    private lateinit var viewModel: PodcastStudioViewModel

    private val article = Article(
        id = "article_1", date = Date(0), title = "Space launch", rawDescription = "raw",
        shortDescription = "A rocket reached orbit.", fullContent = "private full body",
        link = "https://private.example/article", feedId = "private-feed", accountId = 1,
    )
    private val episode = CommuteEpisode(
        id = "episode_1", title = "Space launch", dialogues = listOf(
            CommuteDialogue(CommuteSpeaker.ALEX, "A rocket reached orbit."),
            CommuteDialogue(CommuteSpeaker.SAM, "A notable milestone."),
        ), articleIds = listOf(article.id),
    )
    private val generated = PodcastStudioEpisode(episode, PodcastScriptSource.HEURISTIC)

    @Before fun setUp() {
        context.dataStore.putBlocking(DataStoreKeys.PodcastStudioGeminiConsent, false)
        Dispatchers.setMain(mainDispatcher)
        every { audioPlayer.playerState } returns playerState
        coEvery { scriptService.generate(any(), any(), any()) } returns generated
        viewModel = PodcastStudioViewModel(context, scriptService, audioPlayer, Dispatchers.Unconfined)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun initiateWithUndecidedConsentRequestsConsentBeforeGeneration() {
        forgetConsent()
        viewModel.initiate(article)
        awaitState { it is PodcastStudioUiState.AwaitingConsent }

        assertEquals(article, (viewModel.state.value as PodcastStudioUiState.AwaitingConsent).article)
        coVerify(exactly = 0) { scriptService.generate(any(), any(), any()) }
    }

    @Test fun decliningConsentPersistsFalseAndGeneratesOfflineScript() {
        forgetConsent()
        viewModel.initiate(article)
        awaitState { it is PodcastStudioUiState.AwaitingConsent }
        viewModel.onConsent(false)
        awaitState { it is PodcastStudioUiState.Ready }

        awaitCondition("consent persisted as false") { context.podcastStudioGeminiConsent == false }
        coVerify(exactly = 1) { scriptService.generate(article, false, any()) }
        verify(exactly = 1) { audioPlayer.playEpisode(episode, 0) }
    }

    @Test fun agreeingConsentPersistsTrueAndAllowsGeminiScript() {
        forgetConsent()
        viewModel.initiate(article)
        awaitState { it is PodcastStudioUiState.AwaitingConsent }
        coEvery { scriptService.generate(article, true, any()) } returns generated.copy(source = PodcastScriptSource.GEMINI)
        viewModel.onConsent(true)
        awaitState { it is PodcastStudioUiState.Ready }

        awaitCondition("consent persisted as true") { context.podcastStudioGeminiConsent == true }
        coVerify(exactly = 1) { scriptService.generate(article, true, any()) }
    }

    @Test fun currentlyPlayingEpisodeRequiresExplicitConflictConfirmation() {
        playerState.value = CommutePlayerState(episode = episode, isPlaying = true)
        every { audioPlayer.playerState } returns playerState
        viewModel = PodcastStudioViewModel(context, scriptService, audioPlayer, Dispatchers.Unconfined)
        viewModel.initiate(article)
        awaitState { it is PodcastStudioUiState.ConfirmConflict }

        coVerify(exactly = 0) { scriptService.generate(any(), any(), any()) }
        verify(exactly = 0) { audioPlayer.playEpisode(any(), any()) }
    }

    @Test fun confirmingConflictReplacesTheEpisodeAndStartsPlayback() {
        playerState.value = CommutePlayerState(episode = episode, isPlaying = true)
        every { audioPlayer.playerState } returns playerState
        viewModel = PodcastStudioViewModel(context, scriptService, audioPlayer, Dispatchers.Unconfined)
        viewModel.initiate(article)
        awaitState { it is PodcastStudioUiState.ConfirmConflict }
        viewModel.onConfirmConflict()
        awaitState { it is PodcastStudioUiState.Ready }

        verify(exactly = 1) { audioPlayer.playEpisode(episode, 0) }
    }

    @Test fun dismissingConflictLeavesCurrentPlaybackUntouched() {
        playerState.value = CommutePlayerState(episode = episode, isPlaying = true)
        every { audioPlayer.playerState } returns playerState
        viewModel = PodcastStudioViewModel(context, scriptService, audioPlayer, Dispatchers.Unconfined)
        viewModel.initiate(article)
        awaitState { it is PodcastStudioUiState.ConfirmConflict }
        viewModel.onDismissConflict()

        assertEquals(PodcastStudioUiState.Idle, viewModel.state.value)
        verify(exactly = 0) { audioPlayer.pause() }
        verify(exactly = 0) { audioPlayer.playEpisode(any(), any()) }
    }

    @Test fun dismissingReadySheetPausesPlaybackWithoutDestroyingPlayer() {
        viewModel.initiate(article)
        awaitState { it is PodcastStudioUiState.Ready }
        viewModel.dismissSheet()

        assertEquals(PodcastStudioUiState.Idle, viewModel.state.value)
        verify(exactly = 1) { audioPlayer.pause() }
    }

    @Test fun playerStateChangesAreReflectedInReadyState() {
        viewModel.initiate(article)
        awaitState { it is PodcastStudioUiState.Ready }
        playerState.value = CommutePlayerState(episode = episode, currentDialogueIndex = 1, isPlaying = true)
        awaitState { (it as? PodcastStudioUiState.Ready)?.playerState?.currentDialogueIndex == 1 }

        assertTrue((viewModel.state.value as PodcastStudioUiState.Ready).playerState.isPlaying)
    }

    private fun awaitCondition(what: String, timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for: $what")
            Thread.sleep(10)
        }
    }

    private fun forgetConsent() {
        kotlinx.coroutines.runBlocking {
            context.dataStore.edit { it.remove(DataStoreKeys.PodcastStudioGeminiConsent.key) }
        }
    }

    private fun awaitState(timeoutMs: Long = 3_000, condition: (PodcastStudioUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition(viewModel.state.value)) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out waiting for ViewModel state")
            Thread.sleep(10)
        }
    }
}
