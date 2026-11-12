package com.mckimquyen.reader.ui.component.oracle

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R

private const val SHIMMER_MIN_ALPHA = 0.6f
private const val SHIMMER_MS = 1_200
private const val NO_ANIMATION_SCALE = 0f

/** "Prediction market" marker for an article (ORACLE-01); tapping opens the read-only claim card. */
@Composable
fun OracleBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.oracle_badge_label)
    val action = stringResource(R.string.oracle_badge_open)
    val context = LocalContext.current
    // The shimmer is decoration only: it stops when the user turned system animations off.
    val animate = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != NO_ANIMATION_SCALE
    }
    val shimmer by rememberInfiniteTransition(label = "oracle_shimmer").animateFloat(
        initialValue = if (animate) SHIMMER_MIN_ALPHA else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MS), RepeatMode.Reverse),
        label = "oracle_shimmer_alpha",
    )
    Surface(
        modifier = modifier
            .testTag("oracle_badge")
            .alpha(if (animate) shimmer else 1f)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$label. $action"
            }
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}
