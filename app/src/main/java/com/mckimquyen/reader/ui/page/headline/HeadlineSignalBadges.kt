package com.mckimquyen.reader.ui.page.headline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.headline.HeadlineAnalyzer
import com.mckimquyen.reader.domain.headline.HeadlineSignal
import com.mckimquyen.reader.domain.headline.HeadlineTone

const val CLICKBAIT_BADGE_TAG = "headline_signal_clickbait_badge"
const val TONE_BADGE_TAG = "headline_signal_tone_badge"

@Composable
fun HeadlineSignalBadges(
    signal: HeadlineSignal?,
    modifier: Modifier = Modifier,
    showClickbait: Boolean = true,
    showTone: Boolean = true,
) {
    if (signal == null) return
    val showBait = showClickbait && HeadlineAnalyzer.shouldShowClickbait(signal.clickbaitScore)
    if (!showBait && !showTone) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBait) {
            val label = stringResource(R.string.headline_signals_clickbait_badge, signal.clickbaitScore)
            val desc = stringResource(R.string.headline_signals_clickbait_desc, signal.clickbaitScore)
            Surface(
                modifier = Modifier
                    .testTag(CLICKBAIT_BADGE_TAG)
                    .semantics(mergeDescendants = true) {
                        contentDescription = desc
                    },
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (showTone) {
            val (labelRes, descRes, containerColor, textColor) = when (signal.tone) {
                HeadlineTone.POSITIVE -> ToneVisuals(
                    R.string.headline_signals_tone_positive,
                    R.string.headline_signals_tone_desc_positive,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
                HeadlineTone.UNCLEAR -> ToneVisuals(
                    R.string.headline_signals_tone_unclear,
                    R.string.headline_signals_tone_desc_unclear,
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HeadlineTone.NEGATIVE -> ToneVisuals(
                    R.string.headline_signals_tone_negative,
                    R.string.headline_signals_tone_desc_negative,
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            val toneDescription = stringResource(descRes)
            Surface(
                modifier = Modifier
                    .testTag(TONE_BADGE_TAG)
                    .semantics(mergeDescendants = true) {
                        contentDescription = toneDescription
                    },
                color = containerColor,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = stringResource(labelRes),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private data class ToneVisuals(
    val labelRes: Int,
    val descRes: Int,
    val containerColor: androidx.compose.ui.graphics.Color,
    val textColor: androidx.compose.ui.graphics.Color,
)
