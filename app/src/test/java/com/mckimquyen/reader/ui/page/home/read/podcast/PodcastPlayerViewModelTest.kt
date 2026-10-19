package com.mckimquyen.reader.ui.page.home.read.podcast

import com.mckimquyen.reader.domain.model.article.AudioInfo
import com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState
import com.mckimquyen.reader.domain.sv.PodcastPlaybackController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PodcastPlayerViewModelTest {
    private val flow = MutableStateFlow(PodcastPlaybackState())
    private val controller = mockk<PodcastPlaybackController>(relaxed = true)
    private lateinit var viewModel: PodcastPlayerViewModel
    private val audio = AudioInfo("https://x/a.mp3", "audio/mpeg", 60)

    @Before fun setUp() {
        every { controller.state } returns flow
        viewModel = PodcastPlayerViewModel(controller)
    }

    @Test fun state_isControllerFlow() = assertEquals(flow, viewModel.state)

    @Test fun bind_opensNewEpisode() {
        viewModel.bind("a1", "T", "F", audio)
        verify(exactly = 1) { controller.open("a1", "T", "F", audio) }
    }

    @Test fun bind_sameActiveEpisode_doesNothing() {
        flow.value = PodcastPlaybackState(articleId = "a1")
        viewModel.bind("a1", "T", "F", audio)
        verify(exactly = 0) { controller.open(any(), any(), any(), any()) }
    }

    @Test fun intents_forwardToController() {
        viewModel.onToggle()
        viewModel.onSkip(-15)
        viewModel.onSeek(30)
        viewModel.onSpeed(1.5f)
        viewModel.onSleep(30)
        viewModel.onSleep(null)
        viewModel.onRetry()
        verify(exactly = 1) { controller.toggle() }
        verify(exactly = 1) { controller.skip(-15) }
        verify(exactly = 1) { controller.seekTo(30) }
        verify(exactly = 1) { controller.setSpeed(1.5f) }
        verify(exactly = 1) { controller.startSleepTimer(30) }
        verify(exactly = 1) { controller.cancelSleepTimer() }
        verify(exactly = 1) { controller.retry() }
    }
}
