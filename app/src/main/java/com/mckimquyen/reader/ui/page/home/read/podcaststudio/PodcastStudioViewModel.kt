package com.mckimquyen.reader.ui.page.home.read.podcaststudio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.sv.PodcastStudioScriptService
import com.mckimquyen.reader.infrastructure.audio.CommuteAudioPlayer
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.podcastStudioGeminiConsent
import com.mckimquyen.reader.ui.ext.setPodcastStudioGeminiConsent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Drives the single-article Podcast Studio sheet (EXC-02).
 *
 * The episode lives only in the shared [CommuteAudioPlayer]'s memory. It is deliberately never saved to
 * CommuteEpisodeStore, which would overwrite the user's stored morning bulletin; this class does not even
 * depend on that store.
 */
@HiltViewModel
class PodcastStudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scriptService: PodcastStudioScriptService,
    private val audioPlayer: CommuteAudioPlayer,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<PodcastStudioUiState>(PodcastStudioUiState.Idle)
    val state: StateFlow<PodcastStudioUiState> = _state.asStateFlow()

    private var generationJob: Job? = null
    private var playerJob: Job? = null

    fun initiate(article: Article) {
        // Consent comes first: nothing about the article may reach Gemini before the user has answered.
        if (context.podcastStudioGeminiConsent == null) {
            _state.value = PodcastStudioUiState.AwaitingConsent(article)
            return
        }
        if (audioPlayer.playerState.value.isPlaying) {
            _state.value = PodcastStudioUiState.ConfirmConflict(article)
            return
        }
        generate(article)
    }

    fun onConsent(agreed: Boolean) {
        val pending = (_state.value as? PodcastStudioUiState.AwaitingConsent)?.article ?: return
        viewModelScope.launch(ioDispatcher) {
            context.setPodcastStudioGeminiConsent(agreed)
            if (audioPlayer.playerState.value.isPlaying) {
                _state.value = PodcastStudioUiState.ConfirmConflict(pending)
            } else {
                generate(pending)
            }
        }
    }

    fun onConfirmConflict() {
        val pending = (_state.value as? PodcastStudioUiState.ConfirmConflict)?.article ?: return
        generate(pending)
    }

    fun onDismissConflict() {
        if (_state.value is PodcastStudioUiState.ConfirmConflict) _state.value = PodcastStudioUiState.Idle
    }

    private fun generate(article: Article) {
        generationJob?.cancel()
        _state.value = PodcastStudioUiState.Generating
        generationJob = viewModelScope.launch(ioDispatcher) {
            try {
                val useGemini = context.podcastStudioGeminiConsent == true
                val result = scriptService.generate(article, useGemini, Locale.getDefault().toLanguageTag())
                audioPlayer.playEpisode(result.episode, 0)
                observePlayer(result.episode, result.source)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = PodcastStudioUiState.Error
            }
        }
    }

    private fun observePlayer(
        episode: com.mckimquyen.reader.domain.model.commute.CommuteEpisode,
        source: com.mckimquyen.reader.domain.model.podcast.PodcastScriptSource,
    ) {
        playerJob?.cancel()
        playerJob = viewModelScope.launch(ioDispatcher) {
            audioPlayer.playerState.collect { player ->
                _state.value = PodcastStudioUiState.Ready(episode, source, player)
            }
        }
    }

    fun togglePlayPause() {
        if (audioPlayer.playerState.value.isPlaying) audioPlayer.pause() else audioPlayer.resume()
    }

    fun skipNext() = audioPlayer.skipNext()

    fun skipPrevious() = audioPlayer.skipPrevious()

    fun seekTo(index: Int) = audioPlayer.seekToDialogue(index)

    /** Closing the sheet only pauses: the shared player is a singleton and must not be torn down. */
    fun dismissSheet() {
        playerJob?.cancel()
        generationJob?.cancel()
        if (_state.value is PodcastStudioUiState.Ready) audioPlayer.pause()
        _state.value = PodcastStudioUiState.Idle
    }

    override fun onCleared() {
        playerJob?.cancel()
        generationJob?.cancel()
        super.onCleared()
    }
}
