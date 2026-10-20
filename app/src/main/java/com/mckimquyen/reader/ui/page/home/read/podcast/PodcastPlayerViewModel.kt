package com.mckimquyen.reader.ui.page.home.read.podcast

import androidx.lifecycle.ViewModel
import com.mckimquyen.reader.domain.model.article.AudioInfo
import com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState
import com.mckimquyen.reader.domain.sv.PodcastPlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PodcastPlayerViewModel @Inject constructor(
    private val controller: PodcastPlaybackController,
) : ViewModel() {

    val state: StateFlow<PodcastPlaybackState> = controller.state

    /** Selects the episode unless it is already the active one (so a running session keeps playing). */
    fun bind(articleId: String, title: String, feedName: String, audio: AudioInfo) {
        controller.open(articleId, title, feedName, audio)
    }

    fun playEpisode(articleId: String, title: String, feedName: String, audio: AudioInfo) =
        controller.playEpisode(articleId, title, feedName, audio)

    fun onToggle() = controller.toggle()
    fun onSkip(seconds: Int) = controller.skip(seconds)
    fun onSeek(sec: Int) = controller.seekTo(sec)
    fun onSpeed(speed: Float) = controller.setSpeed(speed)
    fun onSleep(minutes: Int?) = if (minutes == null) controller.cancelSleepTimer() else controller.startSleepTimer(minutes)
    fun onRetry() = controller.retry()
}
