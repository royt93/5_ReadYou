package com.mckimquyen.reader.ui.page.rpg

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.rpg.UserProgress
import com.mckimquyen.reader.domain.model.rpg.WeeklyBrainWrapped
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.sv.AiQuizResult
import com.mckimquyen.reader.domain.sv.AiQuizService
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import com.mckimquyen.reader.infrastructure.rpg.BrainWrappedRenderModel
import com.mckimquyen.reader.infrastructure.rpg.BrainWrappedRenderer
import com.mckimquyen.reader.infrastructure.rpg.BrainWrappedSharer
import com.mckimquyen.reader.ui.ext.quizGeminiConsent
import com.mckimquyen.reader.ui.ext.setQuizGeminiConsent
import com.roy.sdkadbmob.AdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class BrainRpgViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BrainRpgRepository,
    val quizGeneratorService: QuizGeneratorService,
    private val aiQuizService: AiQuizService,
    private val wrappedSharer: BrainWrappedSharer,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val userProgress: StateFlow<UserProgress> = repository.userProgress

    /** Cached ids whose double-XP bonus was taken; Compose reads this instead of querying Room. */
    val doubleRewardedArticleIds: StateFlow<Set<String>> = repository.doubleRewardedArticleIds

    private val _weeklyWrappedState = MutableStateFlow(WeeklyWrappedUiState())
    val weeklyWrappedState: StateFlow<WeeklyWrappedUiState> = _weeklyWrappedState.asStateFlow()

    private val quizCache = QuizCache()
    private val _quizState = MutableStateFlow<QuizUiState>(QuizUiState.Hidden)
    val quizState: StateFlow<QuizUiState> = _quizState.asStateFlow()
    private var quizJob: Job? = null
    private var pendingQuiz: PendingQuiz? = null

    /** What is needed to write a quiz for the article on screen: never the body, only title and short description. */
    private data class PendingQuiz(val articleId: String, val title: String, val shortDescription: String, val category: String)

    /**
     * The end-of-article card appeared. Decides what it shows: a cached quiz, the consent question, the guidance for a
     * declined consent, or a single Gemini request (only when the stored consent is exactly `true`).
     * [content] is used only to pick the XP category on this device; it is never sent anywhere.
     */
    fun onQuizCardShown(articleId: String, title: String, shortDescription: String, content: String) {
        val current = pendingQuiz
        if (current?.articleId == articleId && _quizState.value is QuizUiState.Loading) return
        quizJob?.cancel()
        val request = PendingQuiz(articleId, title, shortDescription, quizGeneratorService.detectCategory(title, content))
        pendingQuiz = request
        quizCache.get(articleId)?.let {
            _quizState.value = QuizUiState.Ready(it)
            return
        }
        quizJob = viewModelScope.launch {
            when (withContext(ioDispatcher) { context.quizGeminiConsent }) {
                null -> _quizState.value = QuizUiState.AwaitingConsent
                false -> _quizState.value = QuizUiState.ConsentDeclined
                true -> generateQuiz(request)
            }
        }
    }

    /** The user answered the in-card question. Agreeing continues with the article that is waiting. */
    fun onQuizConsent(agreed: Boolean) {
        val waiting = pendingQuiz
        quizJob?.cancel()
        quizJob = viewModelScope.launch {
            withContext(ioDispatcher) { context.setQuizGeminiConsent(agreed) }
            if (!agreed) {
                _quizState.value = QuizUiState.ConsentDeclined
            } else if (waiting != null) {
                generateQuiz(waiting)
            }
        }
    }

    /** "Not now": nothing is stored and the question stays, so it is asked again on another article. */
    fun onQuizConsentLater() {
        _quizState.value = QuizUiState.AwaitingConsent
    }

    /** Asks for a new question after a failure. Only the user's tap gets here; nothing retries by itself. */
    fun retryQuizGeneration() {
        val waiting = pendingQuiz ?: return
        if (_quizState.value !is QuizUiState.Failed) return
        quizJob?.cancel()
        quizJob = viewModelScope.launch { generateQuiz(waiting) }
    }

    private suspend fun generateQuiz(request: PendingQuiz) {
        _quizState.value = QuizUiState.Loading
        val result = withContext(ioDispatcher) {
            aiQuizService.generate(request.articleId, request.title, request.shortDescription, request.category, Locale.getDefault().toLanguageTag())
        }
        // No stale-answer check is needed: a newer article cancels [quizJob] first, so withContext above throws
        // CancellationException instead of returning an old answer here.
        _quizState.value = when (result) {
            is AiQuizResult.Ready -> {
                quizCache.put(request.articleId, result.quiz)
                QuizUiState.Ready(result.quiz)
            }
            is AiQuizResult.Failed -> QuizUiState.Failed(result.reason)
        }
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
        refreshWeeklyWrapped()
    }

    fun refreshWeeklyWrapped() {
        viewModelScope.launch {
            _weeklyWrappedState.update { it.copy(isLoading = true, error = null) }
            try {
                val summary = repository.getWeeklyBrainWrapped(
                    now = java.time.Instant.now(),
                    zone = java.time.ZoneId.systemDefault(),
                )
                _weeklyWrappedState.update { it.copy(isLoading = false, summary = summary, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _weeklyWrappedState.update { it.copy(isLoading = false, error = WrappedShareError.SUMMARY_FAILED) }
            }
        }
    }

    fun shareWeeklyWrapped(
        renderModel: BrainWrappedRenderModel,
        chooserTitle: String,
        onReady: (Intent) -> Unit,
    ) {
        val state = _weeklyWrappedState.value
        val summary = state.summary ?: return
        if (!state.canShare) return
        _weeklyWrappedState.update { it.copy(isSharing = true, error = null) }
        viewModelScope.launch {
            try {
                val bitmap = withContext(defaultDispatcher) {
                    BrainWrappedRenderer.render(renderModel.copy(weeklyXp = summary.weeklyXp))
                }
                val file = try {
                    wrappedSharer.writePng(bitmap)
                } finally {
                    bitmap.recycle()
                }
                onReady(wrappedSharer.buildShareIntent(file, chooserTitle))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _weeklyWrappedState.update { it.copy(error = WrappedShareError.WRITE_FAILED) }
            } finally {
                _weeklyWrappedState.update { it.copy(isSharing = false) }
            }
        }
    }

    /**
     * Idempotency lives in [BrainRpgRepository] (persisted), not in this ViewModel: a recreated ViewModel or a killed
     * process must not let the same article be rewarded again. [onRewarded] only fires when XP was really awarded.
     */
    fun onArticleReadFinished(articleId: String, category: String, onRewarded: ((Long) -> Unit)? = null) {
        if (articleId.isBlank()) return
        viewModelScope.launch {
            val awarded = repository.awardReadingXpOnce(articleId, category, READING_XP)
            if (awarded > 0L) onRewarded?.invoke(awarded)
        }
    }

    fun submitQuizAnswer(articleId: String, category: String, isCorrect: Boolean, onResult: (UserProgress, Long) -> Unit) {
        viewModelScope.launch {
            val (updated, xpAwarded) = repository.submitQuizResultOnce(articleId, category, isCorrect)
            onResult(updated, xpAwarded)
        }
    }

    /** Awards the double-XP bonus once; [onDone] gets true only when XP was really added. */
    fun claimDoubleReward(articleId: String, category: String, bonusXp: Long, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            onDone(repository.awardDoubleXpForQuizOnce(articleId, category, bonusXp) > 0L)
        }
    }

    fun doubleQuizReward(activity: Activity, articleId: String, category: String, bonusXp: Long, onDone: (Boolean) -> Unit) {
        // Do not even offer the rewarded ad when the bonus was already taken.
        if (articleId in doubleRewardedArticleIds.value) {
            onDone(false)
            return
        }
        AdManager.showRewarded(activity) { earned ->
            if (earned) claimDoubleReward(articleId, category, bonusXp, onDone) else onDone(false)
        }
    }

    /**
     * Rewarded recovery for ONE decayed category. The ad is only offered when the cached progress shows real decay,
     * and the repository re-validates at claim time (the ad callback is async and may repeat). [onDone] fires once:
     * true only when the category was really reset. [showAd] is injectable so this stays unit-testable.
     */
    fun recoverCategory(
        categoryId: String,
        showAd: ((Boolean) -> Unit) -> Unit,
        onDone: (Boolean) -> Unit,
    ) {
        val progress = userProgress.value
        val base = progress.categoryBaseXp[categoryId] ?: 0L
        val effective = progress.categoryXp[categoryId] ?: 0L
        if (base <= 0L || effective >= base) {
            onDone(false)
            return
        }
        val handled = java.util.concurrent.atomic.AtomicBoolean(false)
        showAd { earned ->
            if (!handled.compareAndSet(false, true)) return@showAd
            if (!earned) {
                onDone(false)
                return@showAd
            }
            viewModelScope.launch { onDone(repository.recoverCategory(categoryId)) }
        }
    }

    fun recoverCategory(activity: Activity, categoryId: String, onDone: (Boolean) -> Unit) =
        recoverCategory(categoryId, showAd = { cb -> AdManager.showRewarded(activity) { earned -> cb(earned) } }, onDone = onDone)

    fun retryQuiz(activity: Activity, onDone: (Boolean) -> Unit) {
        AdManager.showRewarded(activity) { earned ->
            onDone(earned)
        }
    }

    fun activateStreakShield(activity: Activity, onDone: (Boolean) -> Unit) {
        AdManager.showRewarded(activity) { earned ->
            if (earned) {
                viewModelScope.launch {
                    repository.activateStreakShield()
                    onDone(true)
                }
            } else {
                onDone(false)
            }
        }
    }

    private companion object {
        const val READING_XP = 50L
    }
}

data class WeeklyWrappedUiState(
    val isLoading: Boolean = true,
    val summary: WeeklyBrainWrapped? = null,
    val isSharing: Boolean = false,
    val error: WrappedShareError? = null,
) {
    val canShare: Boolean get() = !isLoading && !isSharing && (summary?.weeklyXp ?: 0L) > 0L
}

enum class WrappedShareError {
    SUMMARY_FAILED,
    WRITE_FAILED,
}
