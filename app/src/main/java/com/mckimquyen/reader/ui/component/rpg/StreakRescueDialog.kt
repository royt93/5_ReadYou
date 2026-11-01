package com.mckimquyen.reader.ui.component.rpg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.StreakRescueOffer

const val STREAK_RESCUE_DIALOG_TAG = "streak_rescue_dialog"
const val STREAK_RESCUE_AD_TAG = "streak_rescue_ad_btn"
const val STREAK_RESCUE_RESTART_TAG = "streak_rescue_restart_btn"
const val STREAK_RESCUE_LATER_TAG = "streak_rescue_later_btn"

private val ACTION_SPACING = 4.dp
private val ERROR_SPACING = 8.dp

/**
 * Broken-streak prompt (RPG-03). Material 3 1.0.1 `AlertDialog` has only confirm/dismiss slots, so all three actions
 * live in one column inside `confirmButton`. Outside tap and Back mean "later": they never change saved data.
 */
@Composable
fun StreakRescueDialog(
    offer: StreakRescueOffer,
    isLoadingAd: Boolean,
    errorMessage: String?,
    onRescueWithAd: () -> Unit,
    onRestartFromScratch: () -> Unit,
    onDismissLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.streak_rescue_title)
    val message = stringResource(R.string.streak_rescue_message, offer.missedDays, offer.streakDays)
    val actionAd = stringResource(R.string.streak_rescue_action_ad)
    val actionRestart = stringResource(R.string.streak_rescue_action_restart)
    val actionLater = stringResource(R.string.streak_rescue_action_later)

    AlertDialog(
        onDismissRequest = onDismissLater,
        modifier = modifier.testTag(STREAK_RESCUE_DIALOG_TAG),
        title = { Text("🔥 $title", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (errorMessage != null) {
                    Spacer(Modifier.height(ERROR_SPACING))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ACTION_SPACING)) {
                Button(
                    onClick = onRescueWithAd,
                    enabled = !isLoadingAd,
                    modifier = Modifier.fillMaxWidth().testTag(STREAK_RESCUE_AD_TAG),
                ) { Text("🎬 $actionAd") }
                OutlinedButton(
                    onClick = onRestartFromScratch,
                    enabled = !isLoadingAd,
                    modifier = Modifier.fillMaxWidth().testTag(STREAK_RESCUE_RESTART_TAG),
                ) { Text(actionRestart) }
                TextButton(
                    onClick = onDismissLater,
                    enabled = !isLoadingAd,
                    modifier = Modifier.fillMaxWidth().testTag(STREAK_RESCUE_LATER_TAG),
                ) { Text(actionLater) }
            }
        },
    )
}
