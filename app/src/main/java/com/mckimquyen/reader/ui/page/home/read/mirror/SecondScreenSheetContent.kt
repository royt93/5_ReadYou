package com.mckimquyen.reader.ui.page.home.read.mirror

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import java.text.DateFormat
import java.util.Date

const val SECOND_SCREEN_SWITCH_TAG = "second_screen_switch"
const val SECOND_SCREEN_URL_TAG = "second_screen_url"
const val SECOND_SCREEN_COPY_TAG = "second_screen_copy"
const val SECOND_SCREEN_SHARE_TAG = "second_screen_share"
const val SECOND_SCREEN_WARNING_TAG = "second_screen_warning"
const val SECOND_SCREEN_VIEWERS_TAG = "second_screen_viewers"
const val SECOND_SCREEN_STATUS_TAG = "second_screen_status"
const val SECOND_SCREEN_RETRY_TAG = "second_screen_retry"

private const val MIN_TOUCH_TARGET_DP = 48
private const val CONTENT_GAP_DP = 12
private const val SECTION_PADDING_DP = 16

/** Inline BottomDrawer content: no modal API, matching the app's Material3 1.0.1 drawer pattern. */
@Composable
fun SecondScreenSheetContent(
    state: MirrorUiState,
    onToggle: (Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = state as? MirrorUiState.Running
    val enabled = state is MirrorUiState.Starting || running != null
    val title = stringResource(R.string.second_screen_title)

    Column(
        modifier = modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(SECTION_PADDING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(CONTENT_GAP_DP.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)

        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                .testTag(SECOND_SCREEN_SWITCH_TAG)
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle)
                .semantics { selected = enabled },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP_DP.dp),
        ) {
            Text(stringResource(R.string.second_screen_switch), modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = null)
        }

        if (state != MirrorUiState.Off) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().testTag(SECOND_SCREEN_WARNING_TAG),
            ) {
                Text(
                    stringResource(R.string.second_screen_warning),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(SECTION_PADDING_DP.dp),
                )
            }
        }

        when (state) {
            MirrorUiState.Off -> Unit
            MirrorUiState.Starting -> Status(stringResource(R.string.second_screen_starting))
            MirrorUiState.NoNetwork -> {
                Status(stringResource(R.string.second_screen_no_network))
                RetryButton(onToggle)
            }
            MirrorUiState.NoPermission -> {
                Status(stringResource(R.string.second_screen_lan_permission_denied))
                RetryButton(onToggle)
            }
            MirrorUiState.Expired -> {
                Status(stringResource(R.string.second_screen_expired))
                RetryButton(onToggle)
            }
            is MirrorUiState.Error -> {
                Status(stringResource(R.string.second_screen_error))
                RetryButton(onToggle)
            }
            is MirrorUiState.Running -> RunningContent(state, onCopy, onShare)
        }
    }
}

@Composable
private fun Status(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(SECOND_SCREEN_STATUS_TAG))
}

@Composable
private fun RetryButton(onToggle: (Boolean) -> Unit) {
    OutlinedButton(
        onClick = { onToggle(true) },
        modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(SECOND_SCREEN_RETRY_TAG),
    ) { Text(stringResource(R.string.second_screen_retry)) }
}

@Composable
private fun RunningContent(
    state: MirrorUiState.Running,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val expiry = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.expiresAtMillis))
    Text(stringResource(R.string.second_screen_url_label), style = MaterialTheme.typography.bodyMedium)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(SECOND_SCREEN_URL_TAG),
    ) {
        Text(
            state.url,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(SECTION_PADDING_DP.dp),
        )
    }
    Text(
        stringResource(R.string.second_screen_viewers, state.clients),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(SECOND_SCREEN_VIEWERS_TAG),
    )
    Text(stringResource(R.string.second_screen_expires, expiry), style = MaterialTheme.typography.bodySmall)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP_DP.dp),
    ) {
        Button(
            onClick = { onCopy(state.url) },
            modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(SECOND_SCREEN_COPY_TAG),
        ) { Text(stringResource(R.string.second_screen_copy)) }
        OutlinedButton(
            onClick = { onShare(state.url) },
            modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(SECOND_SCREEN_SHARE_TAG),
        ) { Text(stringResource(R.string.second_screen_share)) }
    }
}
