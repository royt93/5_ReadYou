package com.mckimquyen.reader.ui.component.radar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.page.radar.RadarFlag

/** "Edited" / "Removed" marker for an article (EXC-10); tapping opens the diff screen. */
@Composable
fun RadarBadge(
    flag: RadarFlag,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(if (flag == RadarFlag.REMOVED) R.string.radar_badge_removed else R.string.radar_badge_edited)
    val action = stringResource(R.string.radar_badge_open_diff)
    Surface(
        modifier = modifier
            .testTag("radar_badge_${flag.name}")
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$label. $action"
            }
            .clickable(onClick = onClick),
        color = if (flag == RadarFlag.REMOVED) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (flag == RadarFlag.REMOVED) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}
