package com.mckimquyen.reader.ui.page.digest

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.mckimquyen.reader.domain.model.digest.DailyDigest
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.DailyDigestDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.sv.DailyDigestSv
import com.mckimquyen.reader.infrastructure.ai.DailyDigestContent
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.ui.ext.currentAccountId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DigestViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val digestSv: DailyDigestSv,
    private val digestDao: DailyDigestDao,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow<DigestUiState>(DigestUiState.Loading)
    val state: StateFlow<DigestUiState> = _state.asStateFlow()

    init {
        loadLatest()
    }

    private var generateJob: Job? = null

    fun generate() {
        // A second tap while a brief is being written must not start a second (billable) model call.
        if (generateJob?.isActive == true) return
        generateJob = viewModelScope.launch {
            _state.value = DigestUiState.Loading
            try {
                val accountId = withContext(ioDispatcher) { context.currentAccountId }
                val digest = digestSv.generate(accountId)
                if (digest == null) {
                    _state.value = DigestUiState.Empty
                    return@launch
                }
                _state.value = stateFor(digest, digestSv.history(accountId))
            } catch (error: Exception) {
                Log.e(TAG, "Digest generation failed: ${error.javaClass.simpleName}", error)
                _state.value = DigestUiState.Error
            }
        }
    }

    fun loadLatest() {
        viewModelScope.launch {
            try {
                val history = withContext(ioDispatcher) {
                    digestDao.queryRecent(context.currentAccountId)
                }
                val digest = history.firstOrNull()
                _state.value = if (digest == null) DigestUiState.Empty else stateFor(digest, history)
            } catch (_: Exception) {
                _state.value = DigestUiState.Error
            }
        }
    }

    private suspend fun stateFor(digest: DailyDigest, history: List<DailyDigest>): DigestUiState = withContext(ioDispatcher) {
        // Gson bypasses Kotlin null-safety, so a malformed stored row can yield null fields that would only blow
        // up later during composition. Reject them here, inside the callers' try/catch.
        val content = checkNotNull(Gson().fromJson(digest.contentJson, DailyDigestContent::class.java)) { "empty digest" }
        @Suppress("SENSELESS_COMPARISON") // Gson can leave a non-null Kotlin field null
        check(content.sections != null) { "digest without sections" }
        val sourceIds = checkNotNull(Gson().fromJson(digest.sourceArticleIdsJson, Array<String>::class.java)) {
            "digest without source ids"
        }.toList()
        val sources = sourceIds.mapNotNull { id ->
            val article = articleDao.queryById(id)?.article ?: return@mapNotNull null
            val feed = feedDao.queryById(article.feedId)
            DigestSourceUi(id, article.title.ifBlank { feed?.name.orEmpty() }, article.link)
        }
        DigestUiState.Success(digest, content, sources, history)
    }

    private companion object {
        const val TAG = "DailyDigest"
    }

    fun selectDigest(id: String) {
        viewModelScope.launch {
            try {
                val history = withContext(ioDispatcher) { digestDao.queryRecent(context.currentAccountId) }
                val digest = history.firstOrNull { it.id == id } ?: return@launch
                _state.value = stateFor(digest, history)
            } catch (_: Exception) {
                _state.value = DigestUiState.Error
            }
        }
    }
}
