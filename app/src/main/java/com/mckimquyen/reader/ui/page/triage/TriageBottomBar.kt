package com.mckimquyen.reader.ui.page.triage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R

const val TRIAGE_BTN_SKIP_TAG = "triage_btn_skip"
const val TRIAGE_BTN_READ_NOW_TAG = "triage_btn_read_now"
const val TRIAGE_BTN_SAVE_TAG = "triage_btn_save"

@Composable
fun TriageBottomBar(
    onSkip: () -> Unit,
    onReadNow: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Skip Button (Left)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(
                onClick = onSkip,
                modifier = Modifier
                    .size(56.dp)
                    .testTag(TRIAGE_BTN_SKIP_TAG),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.triage_swipe_left_skip),
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.triage_swipe_left_skip),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Read Now Button (Middle)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(
                onClick = onReadNow,
                modifier = Modifier
                    .size(64.dp)
                    .testTag(TRIAGE_BTN_READ_NOW_TAG),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = stringResource(R.string.triage_swipe_up_read),
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.triage_swipe_up_read),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Save Button (Right)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(
                onClick = onSave,
                modifier = Modifier
                    .size(56.dp)
                    .testTag(TRIAGE_BTN_SAVE_TAG),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = stringResource(R.string.triage_swipe_right_save),
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.triage_swipe_right_save),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
