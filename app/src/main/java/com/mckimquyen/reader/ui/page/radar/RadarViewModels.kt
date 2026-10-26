package com.mckimquyen.reader.ui.page.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.repository.ArticleRadarDao
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Flagged articles only (tiny table), so list rows need no per-item query. */
@HiltViewModel
class RadarFlagsViewModel @Inject constructor(dao: ArticleRadarDao) : ViewModel() {
    val flags: StateFlow<Map<String, RadarFlag>> = dao.observeFlagged()
        .map { states -> states.mapNotNull { s -> s.toFlag()?.let { s.articleId to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

sealed interface ArticleDiffUiState {
    data object Loading : ArticleDiffUiState
    data class Ready(val diff: ArticleDiff) : ArticleDiffUiState
    data object Error : ArticleDiffUiState
}

@HiltViewModel
class ArticleDiffViewModel @Inject constructor(
    dao: ArticleRadarDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val loader = ArticleDiffLoader(dao, ioDispatcher)
    private val _state = MutableStateFlow<ArticleDiffUiState>(ArticleDiffUiState.Loading)
    val state = _state.asStateFlow()

    fun load(articleId: String) {
        viewModelScope.launch {
            _state.value = ArticleDiffUiState.Loading
            _state.value = runCatching { loader.load(articleId) }.fold(
                onSuccess = { ArticleDiffUiState.Ready(it) },
                onFailure = { if (it is kotlinx.coroutines.CancellationException) throw it else ArticleDiffUiState.Error },
            )
        }
    }
}
