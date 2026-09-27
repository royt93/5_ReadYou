package com.mckimquyen.reader.ui.component.commute

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.sv.CommuteContentSelector
import com.mckimquyen.reader.domain.sv.CommuteScriptService
import com.mckimquyen.reader.infrastructure.audio.CommuteAudioPlayer
import com.mckimquyen.reader.infrastructure.audio.CommuteEpisodeStore
import com.mckimquyen.reader.infrastructure.audio.CommutePlayerState
import com.mckimquyen.reader.infrastructure.pref.CommuteTimeBudgetPref
import com.mckimquyen.reader.ui.ext.commuteTimeBudgetMinutes
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CommuteUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val playerState: CommutePlayerState = CommutePlayerState(),
    val selectedBudgetMinutes: Int = CommuteContentSelector.DEFAULT_STANDARD_BUDGET_MINUTES,
)

@HiltViewModel
class CommuteCastViewModel @Inject constructor(
    application: Application,
    private val articleDao: ArticleDao,
    private val scriptService: CommuteScriptService,
    private val audioPlayer: CommuteAudioPlayer,
    private val episodeStore: CommuteEpisodeStore,
    private val contentSelector: CommuteContentSelector,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        CommuteUiState(
            selectedBudgetMinutes = application.commuteTimeBudgetMinutes
        )
    )
    val uiState: StateFlow<CommuteUiState> = _uiState.asStateFlow()

    init {
        // Set synchronously, before any composition can run: the sheet's LaunchedEffect asks for a
        // fresh script the moment it sees no episode and no loading, so a flag set later would race
        // it and regenerate the bulletin the user was already promised.
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            audioPlayer.playerState.collect { pState ->
                _uiState.update { it.copy(playerState = pState) }
            }
        }

        viewModelScope.launch {
            // The player is a singleton that the system may have discarded along with the process,
            // so the stored episode — not its in-memory state — is the source of truth at startup.
            if (audioPlayer.playerState.value.episode == null) {
                episodeStore.load()?.let { audioPlayer.prepareEpisode(it) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun selectTimeBudget(minutes: Int) {
        _uiState.update { it.copy(selectedBudgetMinutes = minutes) }
        // Persisted so the 6 AM CommuteWorker honors the same budget the user picked here (DJ-08).
        CommuteTimeBudgetPref.fromMinutes(minutes).put(getApplication(), viewModelScope)
        val isDeepDive = minutes >= CommuteContentSelector.DEFAULT_DEEP_DIVE_BUDGET_MINUTES
        prepareOrPlay(forceRegenerate = true, isDeepDive = isDeepDive, budgetMinutes = minutes)
    }

    fun prepareOrPlay(
        forceRegenerate: Boolean = false,
        isDeepDive: Boolean = false,
        budgetMinutes: Int = _uiState.value.selectedBudgetMinutes,
    ) {
        val currentEpisode = _uiState.value.playerState.episode
        if (!forceRegenerate && currentEpisode != null && (!isDeepDive || currentEpisode.isDeepDive)) {
            if (!_uiState.value.playerState.isPlaying) {
                audioPlayer.resume()
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, selectedBudgetMinutes = budgetMinutes) }
            try {
                val context = getApplication<Application>()
                val accountId = context.currentAccountId
                val candidatePool = withContext(Dispatchers.IO) {
                    articleDao.queryLatestUnread(
                        accountId,
                        limit = CommuteContentSelector.MAX_CANDIDATES_QUERY_LIMIT
                    )
                }

                val articles = withContext(Dispatchers.Default) {
                    contentSelector.selectArticles(
                        candidates = candidatePool,
                        targetMinutes = budgetMinutes
                    )
                }

                val episode = withContext(Dispatchers.Default) {
                    scriptService.generateScript(articles, isDeepDive = isDeepDive)
                }

                if (isDeepDive) {
                    audioPlayer.unlockDeepDive()
                }
                // Store it too, so an episode generated here survives the process being reclaimed
                // just like the one the morning worker prepares.
                episodeStore.save(episode)
                audioPlayer.playEpisode(episode, startFromIndex = 0)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.playerState.isPlaying) {
            audioPlayer.pause()
        } else {
            if (_uiState.value.playerState.episode != null) {
                audioPlayer.resume()
            } else {
                prepareOrPlay()
            }
        }
    }

    fun skipNext() {
        audioPlayer.skipNext()
    }

    fun skipPrevious() {
        audioPlayer.skipPrevious()
    }

    fun seekTo(index: Int) {
        audioPlayer.seekToDialogue(index)
    }

    fun unlockDeepDiveSuccess() {
        prepareOrPlay(forceRegenerate = true, isDeepDive = true)
    }
}
