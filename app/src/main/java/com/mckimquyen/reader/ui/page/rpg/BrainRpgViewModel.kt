package com.mckimquyen.reader.ui.page.rpg

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import com.mckimquyen.reader.domain.model.rpg.UserProgress
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.sv.QuizGeneratorService
import com.roy.sdkadbmob.AdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrainRpgViewModel @Inject constructor(
    private val repository: BrainRpgRepository,
    val quizGeneratorService: QuizGeneratorService
) : ViewModel() {

    val userProgress: StateFlow<UserProgress> = repository.userProgress

    /**
     * Idempotency lives in [BrainRpgRepository] (persisted), not in this ViewModel: a recreated ViewModel or a killed
     * process must not let the same article be rewarded again. [onRewarded] only fires when XP was really awarded.
     */
    fun onArticleReadFinished(articleId: String, category: String, onRewarded: ((Long) -> Unit)? = null) {
        if (articleId.isBlank()) return
        viewModelScope.launch {
            val awarded = repository.awardReadingXpOnce(articleId, category, BrainRpgRepository.READING_XP)
            if (awarded > 0L) onRewarded?.invoke(awarded)
        }
    }

    fun submitQuizAnswer(articleId: String, category: String, isCorrect: Boolean, onResult: (UserProgress, Long) -> Unit) {
        viewModelScope.launch {
            val (updated, xpAwarded) = repository.submitQuizResultOnce(articleId, category, isCorrect)
            onResult(updated, xpAwarded)
        }
    }

    /** Whether the "double the XP" bonus for this article was already taken (persisted, survives a restart). */
    fun isDoubleRewardClaimed(articleId: String): Boolean = repository.isDoubleRewardClaimed(articleId)

    /** Awards the double-XP bonus once; [onDone] gets true only when XP was really added. */
    fun claimDoubleReward(articleId: String, category: String, bonusXp: Long, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            onDone(repository.awardDoubleXpForQuizOnce(articleId, category, bonusXp) > 0L)
        }
    }

    fun doubleQuizReward(activity: Activity, articleId: String, category: String, bonusXp: Long, onDone: (Boolean) -> Unit) {
        // Do not even offer the rewarded ad when the bonus was already taken.
        if (repository.isDoubleRewardClaimed(articleId)) {
            onDone(false)
            return
        }
        AdManager.showRewarded(activity) { earned ->
            if (earned) claimDoubleReward(articleId, category, bonusXp, onDone) else onDone(false)
        }
    }

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
}
