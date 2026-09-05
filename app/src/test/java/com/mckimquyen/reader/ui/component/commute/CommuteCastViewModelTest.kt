package com.mckimquyen.reader.ui.component.commute

import android.app.Application
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.sv.CommuteScriptService
import com.mckimquyen.reader.infrastructure.audio.CommuteAudioPlayer
import com.mckimquyen.reader.infrastructure.audio.CommutePlayerState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class CommuteCastViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val application = mockk<Application>(relaxed = true)
    private val articleDao = mockk<ArticleDao>(relaxed = true)
    private val scriptService = mockk<CommuteScriptService>(relaxed = true)
    private val audioPlayer = mockk<CommuteAudioPlayer>(relaxed = true)
    private val playerStateFlow = MutableStateFlow(CommutePlayerState())

    private lateinit var viewModel: CommuteCastViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { audioPlayer.playerState } returns playerStateFlow
        viewModel = CommuteCastViewModel(application, articleDao, scriptService, audioPlayer)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_matchesPlayerStateFlow() = runTest(testDispatcher) {
        val uiState = viewModel.uiState.value
        assertFalse(uiState.isLoading)
        assertEquals(null, uiState.errorMessage)
        assertEquals(0, uiState.playerState.currentDialogueIndex)
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
}
