package com.mckimquyen.reader.ui.page.setting.privacy

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.component.base.BaseSwitch
import com.mckimquyen.reader.ui.page.setting.SettingItem
import com.mckimquyen.reader.ui.page.setting.zen.EditionTimeRow
import com.mckimquyen.reader.ui.page.setting.zen.formatTime

private const val MINUTES_PER_HOUR = 60
const val COMMUTE_LISTEN_TIME_ROW_TAG = "commute_listen_time_row"

/** The three morning-episode settings (STRAT-01): Gemini consent, listen time, and headset auto-play. */
@Composable
internal fun CommuteMorningSettingsRows(
    consent: Boolean,
    listenMinuteOfDay: Int,
    autoPlay: Boolean,
    permissionDenied: Boolean,
    activity: Activity,
    onConsent: (Boolean) -> Unit,
    onPickTime: (hour: Int, minute: Int) -> Unit,
    onAutoPlay: (Boolean) -> Unit,
) {
    Column {
        SettingItem(
            title = stringResource(R.string.commute_consent_title),
            desc = stringResource(R.string.commute_consent_message),
            onClick = { onConsent(!consent) },
            action = { BaseSwitch(activated = consent) { onConsent(!consent) } },
        )
        EditionTimeRow(
            label = stringResource(R.string.commute_listen_time_title),
            time = formatTime(listenMinuteOfDay / MINUTES_PER_HOUR, listenMinuteOfDay % MINUTES_PER_HOUR),
            testTag = COMMUTE_LISTEN_TIME_ROW_TAG,
            activity = activity,
            onPick = onPickTime,
        )
        Text(
            text = stringResource(R.string.commute_listen_time_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingItem(
            title = stringResource(R.string.commute_autoplay_title),
            desc = stringResource(R.string.commute_autoplay_desc),
            onClick = { onAutoPlay(!autoPlay) },
            action = { BaseSwitch(activated = autoPlay) { onAutoPlay(!autoPlay) } },
        )
        if (permissionDenied) {
            Text(
                text = stringResource(R.string.commute_autoplay_permission_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
