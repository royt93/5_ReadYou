package com.mckimquyen.reader.ui.component.rpg

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import com.mckimquyen.reader.domain.sv.AiQuizFailure
import com.mckimquyen.reader.ui.page.rpg.QuizUiState

object BrainQuizTags {
    const val CONSENT_AGREE = "quiz_consent_agree"
    const val CONSENT_LATER = "quiz_consent_later"
    const val RETRY_GENERATION = "quiz_retry_generation"
    const val OPEN_PRIVACY = "quiz_open_privacy"
    const val LOADING = "quiz_loading"
    const val AI_LABEL = "quiz_ai_label"
}

/** Draws the end-of-article quiz for each [QuizUiState]; a ready quiz is the existing [BrainQuizCard]. */
@Composable
fun BrainQuizHost(
    state: QuizUiState,
    doubleRewardedIds: Set<String>,
    onConsent: (Boolean) -> Unit,
    onConsentLater: () -> Unit,
    onRetryGeneration: () -> Unit,
    onOpenPrivacySettings: () -> Unit,
    onAnswerSubmitted: (QuizQuestion, Boolean) -> Unit,
    onDoubleXpRequested: (Activity, QuizQuestion, (Boolean) -> Unit) -> Unit,
    onRetryQuizRequested: (Activity, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        QuizUiState.Hidden -> Unit
        QuizUiState.AwaitingConsent -> QuizMessageCard(modifier, R.string.quiz_ai_consent_message) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onConsent(true) }, modifier = Modifier.testTag(BrainQuizTags.CONSENT_AGREE)) {
                    Text(stringResource(R.string.quiz_ai_consent_agree))
                }
                OutlinedButton(onClick = onConsentLater, modifier = Modifier.testTag(BrainQuizTags.CONSENT_LATER)) {
                    Text(stringResource(R.string.quiz_ai_consent_later))
                }
            }
        }
        QuizUiState.ConsentDeclined -> QuizMessageCard(modifier, R.string.quiz_ai_declined_message) {
            OutlinedButton(onClick = onOpenPrivacySettings, modifier = Modifier.testTag(BrainQuizTags.OPEN_PRIVACY)) {
                Text(stringResource(R.string.quiz_ai_open_privacy))
            }
        }
        QuizUiState.Loading -> QuizMessageCard(modifier, R.string.quiz_ai_loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp).testTag(BrainQuizTags.LOADING))
        }
        is QuizUiState.Failed -> QuizMessageCard(modifier, failureText(state.reason)) {
            OutlinedButton(onClick = onRetryGeneration, modifier = Modifier.testTag(BrainQuizTags.RETRY_GENERATION)) {
                Text(stringResource(R.string.quiz_ai_retry))
            }
        }
        is QuizUiState.Ready -> BrainQuizCard(
            quiz = state.quiz,
            doubleAlreadyClaimed = state.quiz.articleId in doubleRewardedIds,
            aiLabel = true,
            onAnswerSubmitted = { ok -> onAnswerSubmitted(state.quiz, ok) },
            onDoubleXpRequested = { act, done -> onDoubleXpRequested(act, state.quiz, done) },
            onRetryQuizRequested = onRetryQuizRequested,
            modifier = modifier,
        )
    }
}

private fun failureText(reason: AiQuizFailure): Int = when (reason) {
    AiQuizFailure.NO_KEY -> R.string.quiz_ai_no_key
    AiQuizFailure.NOTHING_TO_ASK -> R.string.quiz_ai_nothing_to_ask
    AiQuizFailure.NETWORK, AiQuizFailure.REJECTED -> R.string.quiz_ai_failed
}

@Composable
private fun QuizMessageCard(modifier: Modifier, messageRes: Int, actions: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(text = stringResource(messageRes), style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}
