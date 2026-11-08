package com.mckimquyen.reader.ui.page.autopilot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.autopilot.FeedAutopilotSuggestion
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton

const val AUTOPILOT_SWITCH_TAG = "autopilot_switch"
const val AUTOPILOT_CARD_TAG_PREFIX = "autopilot_card_"
const val AUTOPILOT_UNSUB_TAG_PREFIX = "autopilot_unsub_"
const val AUTOPILOT_SNOOZE_TAG_PREFIX = "autopilot_snooze_"
const val AUTOPILOT_KEEP_TAG_PREFIX = "autopilot_keep_"
const val AUTOPILOT_CONFIRM_DIALOG_TAG = "autopilot_confirm_dialog"
const val AUTOPILOT_CONFIRM_BUTTON_TAG = "autopilot_confirm_button"
private val MIN_TOUCH_TARGET = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedAutopilotPage(
    state: FeedAutopilotUiState,
    enabled: Boolean,
    pendingUnsubscribe: PendingUnsubscribe?,
    onBack: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRequestUnsubscribe: (String) -> Unit,
    onSnooze: (String) -> Unit,
    onKeep: (String) -> Unit,
    onConfirmUnsubscribe: () -> Unit,
    onCancelUnsubscribe: () -> Unit,
    onRetry: () -> Unit,
) {
    BaseScaffold(
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.feed_autopilot_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.feed_autopilot_desc), style = MaterialTheme.typography.bodyMedium)
                EnabledRow(enabled, onToggleEnabled)
                StateBody(state, onRequestUnsubscribe, onSnooze, onKeep, onRetry)
            }
        },
    )

    if (pendingUnsubscribe != null) {
        ConfirmDialog(pendingUnsubscribe, onConfirmUnsubscribe, onCancelUnsubscribe)
    }
}

@Composable
private fun EnabledRow(enabled: Boolean, onToggleEnabled: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.feed_autopilot_enabled), modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = onToggleEnabled,
            modifier = Modifier.testTag(AUTOPILOT_SWITCH_TAG),
        )
    }
}

@Composable
private fun StateBody(
    state: FeedAutopilotUiState,
    onRequestUnsubscribe: (String) -> Unit,
    onSnooze: (String) -> Unit,
    onKeep: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        FeedAutopilotUiState.Loading -> CircularProgressIndicator()
        is FeedAutopilotUiState.Clean -> Text(stringResource(R.string.feed_autopilot_summary_clean))
        is FeedAutopilotUiState.HasSuggestions -> {
            Text(
                stringResource(R.string.feed_autopilot_summary_noisy, state.suggestions.size, state.windowDays),
                style = MaterialTheme.typography.titleMedium,
            )
            state.suggestions.forEach { SuggestionCard(it, onRequestUnsubscribe, onSnooze, onKeep) }
        }
        FeedAutopilotUiState.NotLocalAccount -> Text(stringResource(R.string.feed_autopilot_not_local_account))
        FeedAutopilotUiState.RetentionTooShort -> Text(stringResource(R.string.feed_autopilot_retention_too_short))
        FeedAutopilotUiState.Disabled -> Text(stringResource(R.string.feed_autopilot_disabled_message))
        FeedAutopilotUiState.Error -> {
            Text(stringResource(R.string.feed_autopilot_error), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: FeedAutopilotSuggestion,
    onRequestUnsubscribe: (String) -> Unit,
    onSnooze: (String) -> Unit,
    onKeep: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag(AUTOPILOT_CARD_TAG_PREFIX + suggestion.feedId)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(suggestion.feedName, style = MaterialTheme.typography.titleMedium)
            Text(reasonText(suggestion), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { onRequestUnsubscribe(suggestion.feedId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .testTag(AUTOPILOT_UNSUB_TAG_PREFIX + suggestion.feedId),
            ) { Text(stringResource(R.string.feed_autopilot_action_unsubscribe)) }
            OutlinedButton(
                onClick = { onSnooze(suggestion.feedId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .testTag(AUTOPILOT_SNOOZE_TAG_PREFIX + suggestion.feedId),
            ) { Text(stringResource(R.string.feed_autopilot_action_snooze)) }
            OutlinedButton(
                onClick = { onKeep(suggestion.feedId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .testTag(AUTOPILOT_KEEP_TAG_PREFIX + suggestion.feedId),
            ) { Text(stringResource(R.string.feed_autopilot_action_keep)) }
        }
    }
}

@Composable
private fun reasonText(s: FeedAutopilotSuggestion): String =
    if (s.windowEngaged == 0) {
        stringResource(R.string.feed_autopilot_reason_zero, s.windowTotal, s.windowDays)
    } else {
        stringResource(R.string.feed_autopilot_reason_opened, s.windowEngaged, s.windowTotal, s.windowDays, s.engagementPercent)
    }

@Composable
private fun ConfirmDialog(pending: PendingUnsubscribe, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag(AUTOPILOT_CONFIRM_DIALOG_TAG),
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.feed_autopilot_confirm_title, pending.feedName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.feed_autopilot_confirm_message))
                if (pending.starredCount > 0) {
                    Text(
                        stringResource(R.string.feed_autopilot_confirm_starred_warning, pending.starredCount),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(AUTOPILOT_CONFIRM_BUTTON_TAG)) { Text(stringResource(R.string.feed_autopilot_action_unsubscribe)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}
