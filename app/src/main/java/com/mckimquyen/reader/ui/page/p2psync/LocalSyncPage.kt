package com.mckimquyen.reader.ui.page.p2psync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.p2psync.LocalSyncPlan
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton

const val SYNC_SEND_START_TAG = "sync_send_start"
const val SYNC_RECEIVE_TAG = "sync_receive"
const val SYNC_RECEIVE_TAB_TAG = "sync_receive_tab"
const val SYNC_SECRET_VALUE_TAG = "sync_secret_value"
const val SYNC_COPY_URL_TAG = "sync_copy_url"
const val SYNC_COPY_SECRET_TAG = "sync_copy_secret"
const val SYNC_SHARE_URL_TAG = "sync_share_url"
const val SYNC_SHARE_SECRET_TAG = "sync_share_secret"
const val SYNC_URL_FIELD_TAG = "sync_url_field"
const val SYNC_SECRET_FIELD_TAG = "sync_secret_field"
const val SYNC_LOAD_PREVIEW_TAG = "sync_load_preview"
const val SYNC_APPLY_TAG = "sync_apply"
const val SYNC_CONFIRM_APPLY_TAG = "sync_confirm_apply"
const val SYNC_CANCEL_TAG = "sync_cancel"
const val SYNC_STOP_SEND_TAG = "sync_stop_send"
const val SYNC_CANCEL_START_TAG = "sync_cancel_start"
const val SYNC_HTTP_WARNING_TAG = "sync_http_warning"
const val SYNC_SECRET_WARNING_TAG = "sync_secret_warning"

@Composable
fun LocalSyncPage(
    state: LocalSyncUiState,
    isLocal: Boolean,
    onBack: () -> Unit,
    onStartSend: () -> Unit,
    onLoadPreview: (String, String) -> Unit,
    onCancel: () -> Unit,
    onStopSend: () -> Unit,
    onApply: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onCopySecret: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onShareSecret: (String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(SEND_TAB) }
    var url by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var confirmApply by remember { mutableStateOf(false) }
    val sending = state as? LocalSyncUiState.Sending
    val preview = state as? LocalSyncUiState.Preview
    val busy = state == LocalSyncUiState.Starting || state == LocalSyncUiState.Receiving || state == LocalSyncUiState.Applying
    val canReceive = isLocal && !busy && preview == null

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
                Text(stringResource(R.string.local_sync_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.local_sync_http_warning),
                    modifier = Modifier.fillMaxWidth().testTag(SYNC_HTTP_WARNING_TAG),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == SEND_TAB, onClick = { tab = SEND_TAB }, text = { Text(stringResource(R.string.local_sync_send)) })
                    Tab(
                        selected = tab == RECEIVE_TAB,
                        onClick = { tab = RECEIVE_TAB },
                        modifier = Modifier.testTag(SYNC_RECEIVE_TAB_TAG),
                        text = { Text(stringResource(R.string.local_sync_receive)) },
                    )
                }
                when {
                    sending != null -> SendSession(sending, onCopyUrl, onCopySecret, onShareUrl, onShareSecret, onStopSend)
                    preview != null -> SyncPreview(
                        plan = preview.plan,
                        isLocal = isLocal,
                        confirmApply = confirmApply,
                        onCancel = { confirmApply = false; onCancel() },
                        onRequestApply = { confirmApply = true },
                        onConfirmApply = { confirmApply = false; onApply() },
                        onDismissConfirmation = { confirmApply = false },
                    )
                    tab == SEND_TAB -> SendIdle(state, isLocal, busy, onStartSend, onStopSend)
                    else -> ReceiveEntry(
                        state, isLocal, canReceive, url, { url = it }, secret, { secret = it },
                        onLoadPreview = { onLoadPreview(url, secret) },
                    )
                }
                if (state == LocalSyncUiState.Receiving || state == LocalSyncUiState.Starting || state == LocalSyncUiState.Applying) {
                    CircularProgressIndicator()
                }
                (state as? LocalSyncUiState.Error)?.let { error ->
                    Text(stringResource(error.kind.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (state is LocalSyncUiState.Done) {
                    Text(stringResource(R.string.local_sync_done), color = MaterialTheme.colorScheme.primary)
                }
            }
        },
    )
}

@Composable
private fun SendIdle(
    state: LocalSyncUiState,
    isLocal: Boolean,
    busy: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.local_sync_send_instructions), style = MaterialTheme.typography.bodyLarge)
        if (!isLocal) Text(stringResource(R.string.local_sync_not_local), color = MaterialTheme.colorScheme.error)
        Button(
            onClick = onStart,
            enabled = isLocal && !busy,
            modifier = Modifier.fillMaxWidth().height(MIN_TOUCH_TARGET).testTag(SYNC_SEND_START_TAG),
        ) { Text(stringResource(R.string.local_sync_start)) }
        if (state is LocalSyncUiState.Starting) {
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().height(MIN_TOUCH_TARGET).testTag(SYNC_CANCEL_START_TAG)) {
                Text(stringResource(R.string.local_sync_cancel))
            }
        }
    }
}

@Composable
private fun SendSession(
    state: LocalSyncUiState.Sending,
    onCopyUrl: (String) -> Unit,
    onCopySecret: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onShareSecret: (String) -> Unit,
    onStop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.local_sync_server_url), style = MaterialTheme.typography.titleMedium)
        Text(state.url, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onCopyUrl(state.url) }, modifier = Modifier.weight(1f).height(MIN_TOUCH_TARGET).testTag(SYNC_COPY_URL_TAG)) {
                Text(stringResource(R.string.local_sync_copy_url))
            }
            OutlinedButton(onClick = { onShareUrl(state.url) }, modifier = Modifier.weight(1f).height(MIN_TOUCH_TARGET).testTag(SYNC_SHARE_URL_TAG)) {
                Text(stringResource(R.string.local_sync_share_url))
            }
        }
        Text(stringResource(R.string.local_sync_pairing_secret), style = MaterialTheme.typography.titleMedium)
        Text(state.secretHex, modifier = Modifier.testTag(SYNC_SECRET_VALUE_TAG), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.local_sync_secret_warning), modifier = Modifier.testTag(SYNC_SECRET_WARNING_TAG), color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onCopySecret(state.secretHex) }, modifier = Modifier.weight(1f).height(MIN_TOUCH_TARGET).testTag(SYNC_COPY_SECRET_TAG)) {
                Text(stringResource(R.string.local_sync_copy_secret))
            }
            OutlinedButton(onClick = { onShareSecret(state.secretHex) }, modifier = Modifier.weight(1f).height(MIN_TOUCH_TARGET).testTag(SYNC_SHARE_SECRET_TAG)) {
                Text(stringResource(R.string.local_sync_share_secret))
            }
        }
        Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(MIN_TOUCH_TARGET).testTag(SYNC_STOP_SEND_TAG)) {
            Text(stringResource(R.string.local_sync_stop))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReceiveEntry(
    state: LocalSyncUiState,
    isLocal: Boolean,
    canReceive: Boolean,
    url: String,
    onUrlChange: (String) -> Unit,
    secret: String,
    onSecretChange: (String) -> Unit,
    onLoadPreview: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isLocal) Text(stringResource(R.string.local_sync_not_local), color = MaterialTheme.colorScheme.error)
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            enabled = isLocal && state !is LocalSyncUiState.Receiving,
            label = { Text(stringResource(R.string.local_sync_enter_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(SYNC_URL_FIELD_TAG),
        )
        OutlinedTextField(
            value = secret,
            onValueChange = onSecretChange,
            enabled = isLocal && state !is LocalSyncUiState.Receiving,
            label = { Text(stringResource(R.string.local_sync_enter_secret)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth().testTag(SYNC_SECRET_FIELD_TAG),
        )
        Button(
            onClick = onLoadPreview,
            enabled = canReceive,
            modifier = Modifier.fillMaxWidth().height(MIN_TOUCH_TARGET).testTag(SYNC_LOAD_PREVIEW_TAG),
        ) { Text(stringResource(R.string.local_sync_load_preview)) }
    }
}

@Composable
private fun SyncPreview(
    plan: LocalSyncPlan,
    isLocal: Boolean,
    confirmApply: Boolean,
    onCancel: () -> Unit,
    onRequestApply: () -> Unit,
    onConfirmApply: () -> Unit,
    onDismissConfirmation: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.local_sync_preview_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.local_sync_feeds_added, plan.feedsToAdd.size))
                Text(stringResource(R.string.local_sync_feeds_existing, plan.duplicateFeeds))
                Text(stringResource(R.string.local_sync_articles_matched, plan.matchedArticles))
                Text(stringResource(R.string.local_sync_articles_unmatched, plan.unmatchedArticles))
                Text(stringResource(R.string.local_sync_monotonic_warning), color = MaterialTheme.colorScheme.error)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(MIN_TOUCH_TARGET).testTag(SYNC_CANCEL_TAG)) {
                Text(stringResource(R.string.local_sync_cancel))
            }
            Button(
                onClick = onRequestApply,
                enabled = isLocal,
                modifier = Modifier.weight(1f).height(MIN_TOUCH_TARGET).testTag(SYNC_APPLY_TAG),
            ) { Text(stringResource(R.string.local_sync_apply)) }
        }
    }
    if (confirmApply) {
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            title = { Text(stringResource(R.string.local_sync_confirm_apply)) },
            text = { Text(stringResource(R.string.local_sync_confirm_counts, plan.feedsToAdd.size, plan.updates.size)) },
            confirmButton = {
                TextButton(onClick = onConfirmApply, modifier = Modifier.testTag(SYNC_CONFIRM_APPLY_TAG)) {
                    Text(stringResource(R.string.local_sync_apply))
                }
            },
            dismissButton = { TextButton(onClick = onDismissConfirmation) { Text(stringResource(R.string.local_sync_cancel)) } },
        )
    }
}

private fun LocalSyncError.messageRes(): Int = when (this) {
    LocalSyncError.NO_NETWORK -> R.string.local_sync_error_no_network
    LocalSyncError.SERVER -> R.string.local_sync_error_server
    LocalSyncError.URL -> R.string.local_sync_error_url
    LocalSyncError.SECRET -> R.string.local_sync_error_secret
    LocalSyncError.NETWORK -> R.string.local_sync_error_network
    LocalSyncError.PACKAGE -> R.string.local_sync_error_package
    LocalSyncError.NOT_LOCAL -> R.string.local_sync_not_local
    LocalSyncError.DATABASE -> R.string.local_sync_error_database
    LocalSyncError.EXPIRED -> R.string.local_sync_error_expired
    LocalSyncError.LAN_PERMISSION -> R.string.local_sync_lan_permission_denied
    LocalSyncError.PAIRING_BROKEN -> R.string.local_sync_error_pairing_broken
    LocalSyncError.PAIRING_LABEL -> R.string.local_sync_error_pairing_label
    LocalSyncError.PAIRING_LIMIT -> R.string.local_sync_error_pairing_limit
}

private const val SEND_TAB = 0
private const val RECEIVE_TAB = 1
private val MIN_TOUCH_TARGET = 48.dp
