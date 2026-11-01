package com.mckimquyen.reader.ui.page.headline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.headline.ArticleSignalResult
import com.mckimquyen.reader.domain.headline.HeadlineTone

const val ECHO_GAUGES_TAG = "echo_signal_gauges"
const val ECHO_HYPE_BAR_TAG = "echo_signal_hype_bar"
const val ECHO_TONE_BAR_TAG = "echo_signal_tone_bar"
const val ECHO_WARNING_TAG = "echo_signal_warning"

private const val PERCENT_MAX = 100f
private const val TONE_BAR_FULL = 1f
private const val TONE_BAR_UNCLEAR = 0.15f
private const val MIN_TOUCH_TARGET_DP = 48

/** Warning orange; Material has no semantic "warning" role and error red would overstate a wording signal. */
private val WarningOrange = Color(0xFFE65100)

/**
 * Two slim lexical-signal bars under the article metadata. Renders nothing unless the result is noteworthy, so an
 * ordinary article carries no extra block. The whole block is one tap target that opens the details sheet.
 */
@Composable
fun EchoSignalGauges(
    result: ArticleSignalResult?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (result == null || !result.isNoteworthy) return

    val showHype = result.hypeScore >= com.mckimquyen.reader.domain.headline.ArticleSignalAnalyzer.HYPE_NOTEWORTHY_THRESHOLD
    val showTone = result.tone != HeadlineTone.UNCLEAR
    val hypeLabel = stringResource(R.string.echo_hype_label, result.hypeScore)
    val toneLabel = stringResource(toneLabelRes(result.tone))
    val summary = buildString {
        if (showHype) append(hypeLabel)
        if (showHype && showTone) append(", ")
        if (showTone) append(stringResource(R.string.echo_tone_label, toneLabel))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
            .clip(RoundedCornerShape(10.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = summary
            }
            .clickable(onClick = onClick)
            .testTag(ECHO_GAUGES_TAG),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showHype) HypeRow(result, hypeLabel)
            if (showTone) ToneRow(result.tone, toneLabel)
        }
    }
}

@Composable
private fun HypeRow(result: ArticleSignalResult, label: String) {
    val barColor = if (result.hasHypeWarning) WarningOrange else MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (result.hasHypeWarning) {
                // Icon + text, not colour alone, so the warning survives colour-blindness and monochrome themes.
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = WarningOrange,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (result.hasHypeWarning) WarningOrange else MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = result.hypeScore / PERCENT_MAX,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 6.dp)
                .clip(RoundedCornerShape(3.dp))
                .testTag(ECHO_HYPE_BAR_TAG),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
        if (result.hasHypeWarning) {
            Text(
                text = stringResource(R.string.echo_hype_warning),
                modifier = Modifier.testTag(ECHO_WARNING_TAG),
                style = MaterialTheme.typography.labelSmall,
                color = WarningOrange,
            )
        }
    }
}

@Composable
private fun ToneRow(tone: HeadlineTone, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Fill is semantic, not a score: tone has three states, so the bar is full for a clear tone and short for UNCLEAR.
        LinearProgressIndicator(
            progress = if (tone == HeadlineTone.UNCLEAR) TONE_BAR_UNCLEAR else TONE_BAR_FULL,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 6.dp)
                .clip(RoundedCornerShape(3.dp))
                .testTag(ECHO_TONE_BAR_TAG),
            color = toneColor(tone),
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
    }
}

internal fun toneLabelRes(tone: HeadlineTone): Int = when (tone) {
    HeadlineTone.POSITIVE -> R.string.echo_tone_positive
    HeadlineTone.UNCLEAR -> R.string.echo_tone_unclear
    HeadlineTone.NEGATIVE -> R.string.echo_tone_negative
}

@Composable
internal fun toneColor(tone: HeadlineTone): Color = when (tone) {
    HeadlineTone.POSITIVE -> MaterialTheme.colorScheme.tertiary
    HeadlineTone.UNCLEAR -> MaterialTheme.colorScheme.outline
    HeadlineTone.NEGATIVE -> MaterialTheme.colorScheme.error
}
