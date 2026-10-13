package com.mckimquyen.reader.ui.component.rpg

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BrainQuizCardWidgetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val quiz = QuizQuestion(
        articleId = "art-1",
        question = "Which one is right?",
        options = listOf("Wrong option", "Right option"),
        correctAnswerIndex = 1,
        explanation = "Because.",
        category = "tech",
    )

    private fun str(id: Int) = composeRule.activity.getString(id)

    private fun show(doubleAlreadyClaimed: Boolean, onAnswer: (Boolean) -> Unit = {}) = composeRule.setContent {
        BrainQuizCard(
            quiz = quiz,
            doubleAlreadyClaimed = doubleAlreadyClaimed,
            onAnswerSubmitted = onAnswer,
            onDoubleXpRequested = { _, _ -> },
            onRetryQuizRequested = { _, _ -> },
        )
    }

    @Test
    fun correctAnswer_offersTheDoubleXpButton_whenNotClaimedYet() {
        show(doubleAlreadyClaimed = false)
        composeRule.onNodeWithText("Right option").performClick()

        composeRule.onNodeWithText(str(R.string.brain_rpg_correct)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.brain_rpg_double_xp)).assertIsDisplayed()
    }

    @Test
    fun correctAnswer_hidesTheDoubleXpButton_whenTheBonusWasAlreadyTaken() {
        // e.g. the user came back after a restart: the bonus is persisted as claimed.
        show(doubleAlreadyClaimed = true)
        composeRule.onNodeWithText("Right option").performClick()

        composeRule.onNodeWithText(str(R.string.brain_rpg_correct)).assertIsDisplayed()
        composeRule.onAllNodesWithText(str(R.string.brain_rpg_double_xp)).assertCountEquals(0)
        composeRule.onNodeWithText("+300 XP").assertIsDisplayed()
    }

    @Test
    fun theAnswerIsReportedOnce_andLocksTheOptions() {
        val answers = mutableListOf<Boolean>()
        show(doubleAlreadyClaimed = false, onAnswer = { answers += it })

        composeRule.onNodeWithText("Right option").performClick()
        composeRule.onNodeWithText("Wrong option").performClick() // locked after the first answer

        assertEquals(listOf(true), answers)
    }

    @Test
    fun wrongAnswer_neverShowsTheDoubleXpButton() {
        show(doubleAlreadyClaimed = false)
        composeRule.onNodeWithText("Wrong option").performClick()

        composeRule.onNodeWithText(str(R.string.brain_rpg_wrong)).assertIsDisplayed()
        composeRule.onAllNodesWithText(str(R.string.brain_rpg_double_xp)).assertCountEquals(0)
    }
}
