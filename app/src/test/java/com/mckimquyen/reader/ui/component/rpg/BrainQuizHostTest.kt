package com.mckimquyen.reader.ui.component.rpg

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import com.mckimquyen.reader.domain.sv.AiQuizFailure
import com.mckimquyen.reader.ui.page.rpg.QuizUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrainQuizHostTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val quiz = QuizQuestion("art-1", "Which one is right?", listOf("Wrong a", "Right option", "Wrong c", "Wrong d"), 1, "Because.", "tech")
    private fun s(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private val calls = mutableListOf<String>()

    private fun show(state: QuizUiState) {
        rule.setContent {
            BrainQuizHost(
                state = state,
                doubleRewardedIds = emptySet(),
                onConsent = { calls += "consent:$it" },
                onConsentLater = { calls += "later" },
                onRetryGeneration = { calls += "retry" },
                onOpenPrivacySettings = { calls += "privacy" },
                onAnswerSubmitted = { _, ok -> calls += "answer:$ok" },
                onDoubleXpRequested = { _, _, _ -> },
                onRetryQuizRequested = { _, _ -> },
            )
        }
    }

    @Test fun hidden_drawsNothing() {
        show(QuizUiState.Hidden)

        rule.onAllNodesWithTag(BrainQuizTags.CONSENT_AGREE).assertCountEquals(0)
        rule.onAllNodesWithTag(BrainQuizTags.LOADING).assertCountEquals(0)
        rule.onAllNodesWithTag(BrainQuizTags.RETRY_GENERATION).assertCountEquals(0)
    }

    @Test fun awaitingConsent_asksInTheCard_andReportsBothAnswers() {
        show(QuizUiState.AwaitingConsent)

        rule.onNodeWithText(s(R.string.quiz_ai_consent_message)).assertIsDisplayed()
        rule.onNodeWithTag(BrainQuizTags.CONSENT_AGREE).performClick()
        rule.onNodeWithTag(BrainQuizTags.CONSENT_LATER).performClick()

        assertEquals(listOf("consent:true", "later"), calls)
    }

    @Test fun consentDeclined_pointsToPrivacySettings_andOpensThem() {
        show(QuizUiState.ConsentDeclined)

        rule.onNodeWithText(s(R.string.quiz_ai_declined_message)).assertIsDisplayed()
        rule.onNodeWithTag(BrainQuizTags.OPEN_PRIVACY).performClick()

        assertEquals(listOf("privacy"), calls)
    }

    @Test fun loading_saysTheAiIsWriting() {
        show(QuizUiState.Loading)

        rule.onNodeWithText(s(R.string.quiz_ai_loading)).assertIsDisplayed()
        rule.onNodeWithTag(BrainQuizTags.LOADING).assertIsDisplayed()
    }

    @Test fun failed_saysThereIsNoAiQuestion_andRetryIsReported() {
        show(QuizUiState.Failed(AiQuizFailure.NETWORK))

        rule.onNodeWithText(s(R.string.quiz_ai_failed)).assertIsDisplayed()
        rule.onNodeWithTag(BrainQuizTags.RETRY_GENERATION).performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test fun failedForNothingToAsk_hasItsOwnExplanation_notTheGenericOne() {
        show(QuizUiState.Failed(AiQuizFailure.NOTHING_TO_ASK))

        rule.onNodeWithText(s(R.string.quiz_ai_nothing_to_ask)).assertIsDisplayed()
    }

    @Test fun failedForNoKey_hasItsOwnExplanation() {
        show(QuizUiState.Failed(AiQuizFailure.NO_KEY))

        rule.onNodeWithText(s(R.string.quiz_ai_no_key)).assertIsDisplayed()
    }

    @Test fun ready_showsTheQuestionTheOptionsAndTheAiLabel() {
        show(QuizUiState.Ready(quiz))

        rule.onNodeWithText("Which one is right?").assertIsDisplayed()
        rule.onNodeWithText("Right option").assertIsDisplayed()
        rule.onNodeWithText(s(R.string.quiz_ai_label)).assertIsDisplayed()
    }

    @Test fun ready_answeringReportsTheQuizAndTheResult() {
        show(QuizUiState.Ready(quiz))

        rule.onNodeWithText("Right option").performClick()

        assertEquals(listOf("answer:true"), calls)
    }
}
