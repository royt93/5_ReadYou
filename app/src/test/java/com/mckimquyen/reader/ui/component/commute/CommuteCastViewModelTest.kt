package com.mckimquyen.reader.ui.component.commute

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.sv.CommuteScriptService
import com.mckimquyen.reader.infrastructure.audio.CommuteAudioPlayer
import com.mckimquyen.reader.infrastructure.audio.CommuteEpisodeStore
import com.mckimquyen.reader.infrastructure.audio.CommutePlayerState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

// Robolectric supplies a real Application: `currentAccountId` reads DataStore, which a mocked
// Application cannot serve, so generating an episode would fail before reaching the store.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CommuteCastViewModelTest {

    // Unconfined rather than Standard: prepareOrPlay suspends on the real Dispatchers.IO, and a
    // Standard dispatcher stops running tasks once advanceUntilIdle returns, so the continuation
    // after that hop would never resume and the test would hang on its own scheduler.
    private val testDispatcher = UnconfinedTestDispatcher()
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val articleDao = mockk<ArticleDao>(relaxed = true)
    private val scriptService = mockk<CommuteScriptService>(relaxed = true)
    private val audioPlayer = mockk<CommuteAudioPlayer>(relaxed = true)
    private val episodeStore = mockk<CommuteEpisodeStore>(relaxed = true)
    private val playerStateFlow = MutableStateFlow(CommutePlayerState())

    private val contentSelector = mockk<com.mckimquyen.reader.domain.sv.CommuteContentSelector>(relaxed = true)

    private val storedEpisode = CommuteEpisode(
        id = "ep_stored",
        title = "Stored Bulletin",
        date = Date(),
        dialogues = listOf(CommuteDialogue(CommuteSpeaker.ALEX, "From storage")),
    )

    private lateinit var viewModel: CommuteCastViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { audioPlayer.playerState } returns playerStateFlow
        coEvery { episodeStore.load() } returns null
        coEvery { episodeStore.save(any()) } just Runs
        viewModel = CommuteCastViewModel(application, articleDao, scriptService, audioPlayer, episodeStore, contentSelector)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun staysLoadingWhileTheStoredEpisodeIsStillBeingLookedUp() = runTest(testDispatcher) {
        // The sheet's LaunchedEffect asks for a fresh script the moment it sees no episode and no
        // loading, so the flag must already be set while the lookup is in flight — otherwise the
        // bulletin the user was promised gets regenerated into a different one.
        val lookup = CompletableDeferred<CommuteEpisode?>()
        coEvery { episodeStore.load() } coAnswers { lookup.await() }

        val vm = CommuteCastViewModel(application, articleDao, scriptService, audioPlayer, episodeStore, contentSelector)

        assertTrue("Must report loading until the stored episode is known", vm.uiState.value.isLoading)
        assertEquals(null, vm.uiState.value.errorMessage)

        lookup.complete(null)
        advanceUntilIdle()
        assertFalse("Loading must end once the lookup finishes", vm.uiState.value.isLoading)
    }

    @Test
    fun restoringAStoredEpisode_doesNotCallTheScriptService() = runTest(testDispatcher) {
        coEvery { episodeStore.load() } returns storedEpisode
        viewModel = CommuteCastViewModel(application, articleDao, scriptService, audioPlayer, episodeStore, contentSelector)
        advanceUntilIdle()

        verify { audioPlayer.prepareEpisode(storedEpisode) }
        coVerify(exactly = 0) { scriptService.generateScript(any(), any(), any()) }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun togglePlayPause_whenPlaying_callsPause() = runTest(testDispatcher) {
        playerStateFlow.value = CommutePlayerState(
            isPlaying = true,
            episode = CommuteEpisode("ep1", "Morning", Date(), listOf(CommuteDialogue(CommuteSpeaker.ALEX, "Hi")))
        )
        advanceUntilIdle()

        viewModel.togglePlayPause()
        verify { audioPlayer.pause() }
    }

    @Test
    fun togglePlayPause_whenPausedWithEpisode_callsResume() = runTest(testDispatcher) {
        playerStateFlow.value = CommutePlayerState(
            isPlaying = false,
            episode = CommuteEpisode("ep1", "Morning", Date(), listOf(CommuteDialogue(CommuteSpeaker.ALEX, "Hi")))
        )
        advanceUntilIdle()

        viewModel.togglePlayPause()
        verify { audioPlayer.resume() }
    }

    @Test
    fun skipControls_delegateToAudioPlayer() = runTest(testDispatcher) {
        viewModel.skipNext()
        verify { audioPlayer.skipNext() }

        viewModel.skipPrevious()
        verify { audioPlayer.skipPrevious() }

        viewModel.seekTo(3)
        verify { audioPlayer.seekToDialogue(3) }
    }

    @Test
    fun generatingANewEpisode_alsoPersistsIt() = runTest(testDispatcher) {
        val sampleArticle = mockk<Article>(relaxed = true)
        val generated = storedEpisode.copy(id = "ep_generated")
        coEvery { articleDao.queryLatestUnread(any(), any()) } returns listOf(sampleArticle)
        every { contentSelector.selectArticles(any(), any(), any(), any()) } returns listOf(sampleArticle)
        coEvery { scriptService.generateScript(any(), any(), any()) } returns generated

        viewModel.prepareOrPlay(forceRegenerate = true)
        advanceUntilIdle()

        // Surface the real cause instead of a bare "save was not called" if generation threw.
        assertEquals(
            "prepareOrPlay reported an error",
            null,
            viewModel.uiState.value.errorMessage,
        )

        // prepareOrPlay hops onto the real Dispatchers.IO/Default, which the test scheduler does
        // not drive, so wait for the call rather than assuming advanceUntilIdle covered it.
        coVerify(timeout = VERIFY_TIMEOUT_MS, exactly = 1) { episodeStore.save(generated) }
        verify(timeout = VERIFY_TIMEOUT_MS) { audioPlayer.playEpisode(generated, startFromIndex = 0) }
    }

    @Test
    fun selectTimeBudget_updatesStateAndTriggersRegeneration() = runTest(testDispatcher) {
        val sampleArticle = mockk<Article>(relaxed = true)
        val generated = storedEpisode.copy(id = "ep_8min")
        coEvery { articleDao.queryLatestUnread(any(), any()) } returns listOf(sampleArticle)
        every { contentSelector.selectArticles(any(), targetMinutes = 8, any(), any()) } returns listOf(sampleArticle)
        coEvery { scriptService.generateScript(any(), any(), any()) } returns generated

        viewModel.selectTimeBudget(8)
        advanceUntilIdle()

        assertEquals(8, viewModel.uiState.value.selectedBudgetMinutes)
        verify(timeout = VERIFY_TIMEOUT_MS) { contentSelector.selectArticles(any(), targetMinutes = 8, any(), any()) }
    }

    private companion object {
        const val VERIFY_TIMEOUT_MS = 5_000L
    }
}
