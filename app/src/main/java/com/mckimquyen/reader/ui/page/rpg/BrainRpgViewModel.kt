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

    private val readArticleIds = mutableSetOf<String>()

    fun onArticleReadFinished(articleId: String, category: String, onRewarded: ((Long) -> Unit)? = null) {
        if (articleId.isBlank()) return
        if (readArticleIds.add(articleId)) {
            viewModelScope.launch {
                repository.addReadingXp(category, 50L)
                onRewarded?.invoke(50L)
            }
        }
    }

    fun submitQuizAnswer(category: String, isCorrect: Boolean, onResult: (UserProgress, Long) -> Unit) {
        viewModelScope.launch {
            val (updated, xpAwarded) = repository.submitQuizResult(category, isCorrect)
            onResult(updated, xpAwarded)
        }
    }

    fun doubleQuizReward(activity: Activity, category: String, bonusXp: Long, onDone: (Boolean) -> Unit) {
        AdManager.showRewarded(activity) { earned ->
            if (earned) {
                viewModelScope.launch {
                    repository.awardDoubleXpForQuiz(category, bonusXp)
                    onDone(true)
                }
            } else {
                onDone(false)
            }
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
