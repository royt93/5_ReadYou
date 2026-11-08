package com.mckimquyen.reader.ui.page.home.read.podcaststudio

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.podcast.PodcastScriptSource
import com.mckimquyen.reader.infrastructure.audio.CommutePlayerState

sealed interface PodcastStudioUiState {
    /** Nothing is open. */
    data object Idle : PodcastStudioUiState

    /** First use: the user has not yet said whether the title and preview may go to Gemini. */
    data class AwaitingConsent(val article: Article) : PodcastStudioUiState

    /** The shared player is already speaking something else; replacing it needs an explicit yes. */
    data class ConfirmConflict(val article: Article) : PodcastStudioUiState

    data object Generating : PodcastStudioUiState

    data class Ready(
        val episode: CommuteEpisode,
        val source: PodcastScriptSource,
        val playerState: CommutePlayerState,
    ) : PodcastStudioUiState

    data object Error : PodcastStudioUiState
}
