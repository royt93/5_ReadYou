package com.mckimquyen.reader.ui.page.rsvp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.rsvp.CalibrationQuestion
import com.mckimquyen.reader.domain.rsvp.Confidence
import com.mckimquyen.reader.domain.rsvp.RsvpCalibrationPlan
import com.mckimquyen.reader.domain.rsvp.RsvpSuggestion
import com.mckimquyen.reader.domain.rsvp.RsvpTiming
import com.mckimquyen.reader.ui.ext.DialogEdgeToEdge
import kotlinx.coroutines.delay

const val ZEN_CALIBRATION_START_TAG = "zen_calibration_start_btn"
const val ZEN_CALIBRATION_APPLY_TAG = "zen_calibration_apply_btn"
const val ZEN_CALIBRATION_SKIP_TAG = "zen_calibration_skip_btn"
const val ZEN_CALIBRATION_TRAIN_BUTTON_TAG = "zen_calibration_train_btn"
const val ZEN_CALIBRATION_OPTION_TAG_PREFIX = "zen_calibration_option_"
const val ZEN_CALIBRATION_SUBMIT_TAG = "zen_calibration_submit_btn"

private const val MIN_TOUCH_TARGET_DP = 48

@Composable
fun RsvpCalibrationDialog(
    viewModel: RsvpCalibrationViewModel,
    onApplied: (RsvpSuggestion) -> Unit,
    onDismiss: () -> Unit,
) {
    val step by viewModel.step.collectAsState()

    LaunchedEffect(step) {
        if (step is CalibrationStep.Finished) onDismiss()
    }

    Dialog(
        onDismissRequest = { viewModel.cancel(); onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogEdgeToEdge()
        RsvpCalibrationScreen(
            step = step,
            onStart = viewModel::startCalibration,
            onPassageFinished = viewModel::onPassageFinished,
            onSubmitAnswers = viewModel::submitAnswers,
            onApply = { viewModel.applySuggestion(onApplied) },
            onSkip = { viewModel.cancel(); onDismiss() },
            onDismiss = { viewModel.cancel(); onDismiss() },
        )
    }
}

@Composable
fun RsvpCalibrationScreen(
    step: CalibrationStep,
    onStart: () -> Unit,
    onPassageFinished: () -> Unit,
    onSubmitAnswers: (List<Int>) -> Unit,
    onApply: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                }
                Text(
                    text = stringResource(R.string.zen_calibration_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(MIN_TOUCH_TARGET_DP.dp))
            }

            Spacer(Modifier.height(16.dp))

            when (step) {
                CalibrationStep.Intro -> IntroContent(onStart)
                is CalibrationStep.Reading -> ReadingContent(step, onPassageFinished)
                is CalibrationStep.Questions -> QuestionsContent(step, onSubmitAnswers)
                is CalibrationStep.Result -> ResultContent(step.suggestion, onApply, onSkip)
                CalibrationStep.Finished -> Unit
            }
        }
    }
}

@Composable
private fun IntroContent(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "⚡ " + stringResource(R.string.zen_calibration_intro_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.zen_calibration_intro_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.zen_calibration_limitation),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(ZEN_CALIBRATION_START_TAG),
        ) {
            Text(stringResource(R.string.zen_calibration_start))
        }
    }
}

@Composable
private fun ReadingContent(step: CalibrationStep.Reading, onFinished: () -> Unit) {
    val tokens = remember(step.text) { RsvpTokenizer.tokenize(step.text) }
    var currentIndex by remember(step.text) { mutableIntStateOf(0) }

    LaunchedEffect(step.text, step.wpm) {
        if (tokens.isEmpty()) {
            onFinished()
            return@LaunchedEffect
        }
        while (currentIndex < tokens.size) {
            val token = tokens[currentIndex]
            delay(RsvpTiming.tokenDelayMs(step.wpm, token.extraDelayMs))
            currentIndex++
        }
        delay(400)
        onFinished()
    }

    val currentToken = tokens.getOrNull(currentIndex)
    val progress = if (tokens.isEmpty()) 1f else (currentIndex.toFloat() / tokens.size.coerceAtLeast(1))

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(
                text = stringResource(R.string.zen_calibration_round_header, step.roundIndex + 1, step.wpm),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (currentToken != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentToken.prefix,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = currentToken.orpChar.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = currentToken.suffix,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Text(
            text = "${step.wpm} WPM",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun QuestionsContent(
    step: CalibrationStep.Questions,
    onSubmit: (List<Int>) -> Unit,
) {
    val selectedAnswers = remember(step.roundIndex) {
        mutableStateListOf<Int>().apply { repeat(step.questions.size) { add(-1) } }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = stringResource(R.string.zen_calibration_questions_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))

            step.questions.forEachIndexed { qIndex, question ->
                Text(
                    text = "${qIndex + 1}. ${question.prompt}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                question.options.forEachIndexed { oIndex, option ->
                    val isSelected = selectedAnswers[qIndex] == oIndex
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                            .testTag("${ZEN_CALIBRATION_OPTION_TAG_PREFIX}${qIndex}_$oIndex")
                            .clickable { selectedAnswers[qIndex] = oIndex },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = isSelected, onClick = { selectedAnswers[qIndex] = oIndex })
                            Text(text = option, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        val allAnswered = selectedAnswers.all { it >= 0 }
        Button(
            onClick = { onSubmit(selectedAnswers.toList()) },
            enabled = allAnswered,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(ZEN_CALIBRATION_SUBMIT_TAG),
        ) {
            Text(stringResource(R.string.confirm))
        }
    }
}

@Composable
private fun ResultContent(
    suggestion: RsvpSuggestion,
    onApply: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "🎯 " + stringResource(R.string.zen_calibration_result_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.zen_calibration_suggested_wpm, suggestion.wpm),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.zen_calibration_limitation),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (suggestion.confidence == Confidence.LOW) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.zen_calibration_low_confidence_note),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(ZEN_CALIBRATION_APPLY_TAG),
        ) {
            Text(stringResource(R.string.zen_calibration_apply))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(ZEN_CALIBRATION_SKIP_TAG),
        ) {
            Text(stringResource(R.string.zen_calibration_skip))
        }
    }
}
