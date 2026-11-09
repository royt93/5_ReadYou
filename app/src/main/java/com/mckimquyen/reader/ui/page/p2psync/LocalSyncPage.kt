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
import com.mckimquyen.reader.domain.p2psync.PairedPeer
import com.mckimquyen.reader.domain.p2psync.PairingStatus
import com.mckimquyen.reader.infrastructure.p2psync.DiscoveredPeer
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
const val SYNC_PAIR_LABEL_TAG = "sync_pair_label"
const val SYNC_RECEIVE_LABEL_TAG = "sync_receive_label"
const val SYNC_PAIR_NEW_TAG = "sync_pair_new"
const val SYNC_PEER_ROW_TAG = "sync_peer_row_"
const val SYNC_PEER_FORGET_TAG = "sync_peer_forget_"
const val SYNC_PEER_CONFIRM_TAG = "sync_peer_confirm_"
const val SYNC_PEER_RECEIVE_TAG = "sync_peer_receive_"
const val SYNC_ALLOW_LAN_TAG = "sync_allow_lan"
const val SYNC_OPEN_SETTINGS_TAG = "sync_open_settings"

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
    pairedPeers: List<PairedPeer> = emptyList(),
    nearby: List<DiscoveredPeer> = emptyList(),
    onPairNew: (String) -> Unit = {},
    onReceiveFromPeer: (String, DiscoveredPeer) -> Unit = { _, _ -> },
    onForgetPeer: (String) -> Unit = {},
    onConfirmPeer: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onRequestLanPermission: () -> Unit = {},
    onLoadPreviewAndPair: (String, String, String) -> Unit = { _, _, _ -> },
) {
    var tab by remember { mutableIntStateOf(SEND_TAB) }
    var url by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var confirmApply by remember { mutableStateOf(false) }
    var pairLabel by remember { mutableStateOf("") }
    var receiveLabel by remember { mutableStateOf("") }
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
                    tab == SEND_TAB -> SendIdle(state, isLocal, busy, onStartSend, onStopSend, pairLabel, { pairLabel = it }, onPairNew)
                    else -> ReceiveEntry(
                        state, isLocal, canReceive, url, { url = it }, secret, { secret = it },
                        receiveLabel = receiveLabel,
                        onReceiveLabelChange = { receiveLabel = it },
                        onLoadPreview = {
                            val label = receiveLabel.trim()
                            if (label.isEmpty()) onLoadPreview(url, secret) else onLoadPreviewAndPair(url, secret, label)
                        },
                        pairedPeers = pairedPeers,
                        nearby = nearby,
                        onReceiveFromPeer = onReceiveFromPeer,
                        onForgetPeer = onForgetPeer,
                        onConfirmPeer = onConfirmPeer,
                    )
                }
                if (state == LocalSyncUiState.Receiving || state == LocalSyncUiState.Starting || state == LocalSyncUiState.Applying) {
                    CircularProgressIndicator()
                }
                (state as? LocalSyncUiState.Error)?.let { error ->
                    Text(stringResource(error.kind.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    if (error.kind == LocalSyncError.LAN_PERMISSION) {
                        Button(
                            onClick = onRequestLanPermission,
                            modifier = Modifier.fillMaxWidth().height(MIN_TOUCH_TARGET).testTag(SYNC_ALLOW_LAN_TAG),
                        ) { Text(stringResource(R.string.local_sync_allow_lan)) }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth().height(MIN_TOUCH_TARGET).testTag(SYNC_OPEN_SETTINGS_TAG),
                        ) { Text(stringResource(R.string.local_sync_open_settings)) }
                    }
                }
                if (state is LocalSyncUiState.Done) {
                    Text(stringResource(R.string.local_sync_done), color = MaterialTheme.colorScheme.primary)
                }
            }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SendIdle(
    state: LocalSyncUiState,
    isLocal: Boolean,
    busy: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    pairLabel: String,
    onPairLabelChange: (String) -> Unit,
    onPairNew: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.local_sync_send_instructions), style = MaterialTheme.typography.bodyLarge)
        if (!isLocal) Text(stringResource(R.string.local_sync_not_local), color = MaterialTheme.colorScheme.error)
        Button(
            onClick = onStart,
            enabled = isLocal && !busy,
            modifier = Modifier.fillMaxWidth().height(MIN_TOUCH_TARGET).testTag(SYNC_SEND_START_TAG),
        ) { Text(stringResource(R.string.local_sync_start)) }
        OutlinedTextField(
            value = pairLabel,
            onValueChange = onPairLabelChange,
            enabled = isLocal && !busy,
            label = { Text(stringResource(R.string.local_sync_pair_label_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(SYNC_PAIR_LABEL_TAG),
        )
        OutlinedButton(
            onClick = { onPairNew(pairLabel.trim()) },
            enabled = isLocal && !busy && pairLabel.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(MIN_TOUCH_TARGET).testTag(SYNC_PAIR_NEW_TAG),
        ) { Text(stringResource(R.string.local_sync_pair_new)) }
        Text(stringResource(R.string.local_sync_pairing_limits), style = MaterialTheme.typography.bodySmall)
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
    receiveLabel: String,
    onReceiveLabelChange: (String) -> Unit,
    onLoadPreview: () -> Unit,
    pairedPeers: List<PairedPeer>,
    nearby: List<DiscoveredPeer>,
    onReceiveFromPeer: (String, DiscoveredPeer) -> Unit,
    onForgetPeer: (String) -> Unit,
    onConfirmPeer: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isLocal) Text(stringResource(R.string.local_sync_not_local), color = MaterialTheme.colorScheme.error)
        if (pairedPeers.isNotEmpty()) {
            Text(stringResource(R.string.local_sync_paired_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (nearby.isEmpty()) R.string.local_sync_no_peer_found else R.string.local_sync_nearby_title),
                style = MaterialTheme.typography.bodySmall,
            )
            pairedPeers.forEach { peer ->
                PeerRow(peer, canReceive && nearby.isNotEmpty() && !peer.needsRepair, { nearby.firstOrNull()?.let { onReceiveFromPeer(peer.peerId, it) } }, onForgetPeer, onConfirmPeer)
            }
        }
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
        OutlinedTextField(
            value = receiveLabel,
            onValueChange = onReceiveLabelChange,
            enabled = isLocal && state !is LocalSyncUiState.Receiving,
            label = { Text(stringResource(R.string.local_sync_remember_label_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(SYNC_RECEIVE_LABEL_TAG),
        )
        Button(
            onClick = onLoadPreview,
            enabled = canReceive,
            modifier = Modifier.fillMaxWidth().height(MIN_TOUCH_TARGET).testTag(SYNC_LOAD_PREVIEW_TAG),
        ) { Text(stringResource(R.string.local_sync_load_preview)) }
    }
}

@Composable
private fun PeerRow(
    peer: PairedPeer,
    canReceive: Boolean,
    onReceive: () -> Unit,
    onForget: (String) -> Unit,
    onConfirm: (String) -> Unit,
) {
    val status = when {
        peer.needsRepair -> R.string.local_sync_peer_needs_repair
        peer.status == PairingStatus.CONFIRMED -> R.string.local_sync_peer_confirmed
        else -> R.string.local_sync_peer_unconfirmed
    }
    Card(modifier = Modifier.fillMaxWidth().testTag(SYNC_PEER_ROW_TAG + peer.peerId), colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(peer.label, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(status), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onReceive,
                    enabled = canReceive,
                    modifier = Modifier.weight(1f).height(MIN_TOUCH_TARGET).testTag(SYNC_PEER_RECEIVE_TAG + peer.peerId),
                ) { Text(stringResource(R.string.local_sync_receive)) }
                if (peer.status == PairingStatus.UNCONFIRMED && !peer.needsRepair) {
                    OutlinedButton(
                        onClick = { onConfirm(peer.peerId) },
                        modifier = Modifier.weight(1f).height(MIN_TOUCH_TARGET).testTag(SYNC_PEER_CONFIRM_TAG + peer.peerId),
                    ) { Text(stringResource(R.string.local_sync_confirm_peer)) }
                }
                OutlinedButton(
                    onClick = { onForget(peer.peerId) },
                    modifier = Modifier.weight(1f).height(MIN_TOUCH_TARGET).testTag(SYNC_PEER_FORGET_TAG + peer.peerId),
                ) { Text(stringResource(R.string.local_sync_forget_peer)) }
            }
        }
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
