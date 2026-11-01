package com.mckimquyen.reader.ui.page.headline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.headline.ArticleSignalResult
import com.mckimquyen.reader.domain.headline.FlaggedPassage
import com.mckimquyen.reader.domain.headline.HypeCueCategory

const val ECHO_SHEET_TAG = "echo_signal_sheet"
const val ECHO_DISCLAIMER_TAG = "echo_signal_disclaimer"
const val ECHO_CLOSE_TAG = "echo_signal_close"
const val ECHO_PASSAGE_TAG_PREFIX = "echo_signal_passage_"

private const val MAX_LIST_HEIGHT_DP = 320

/** Detail sheet: both signals, an honest disclaimer, and up to 8 flagged paragraphs with their wording cues. */
@Composable
fun EchoSignalDetailsSheet(
    result: ArticleSignalResult,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .testTag(ECHO_SHEET_TAG),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.echo_details_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onClose, modifier = Modifier.testTag(ECHO_CLOSE_TAG)) {
                Text(stringResource(R.string.close))
            }
        }
        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.echo_hype_label, result.hypeScore),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (result.hasHypeWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(toneLabelRes(result.tone)),
            style = MaterialTheme.typography.bodyMedium,
            color = toneColor(result.tone),
        )
        Spacer(Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ECHO_DISCLAIMER_TAG),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ) {
            Text(
                text = stringResource(R.string.echo_disclaimer),
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))

        if (result.flaggedPassages.isEmpty()) {
            Text(
                text = stringResource(R.string.echo_no_passages),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.echo_flagged_passages_title, result.flaggedPassages.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = MAX_LIST_HEIGHT_DP.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(result.flaggedPassages) { index, passage -> PassageCard(index, passage) }
            }
        }
    }
}

@Composable
private fun PassageCard(index: Int, passage: FlaggedPassage) {
    // stringResource is @Composable, so resolve every label here instead of inside the joinToString lambda.
    val cueLabels = passage.cues.map { stringResource(cueLabelRes(it)) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ECHO_PASSAGE_TAG_PREFIX + index),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = passage.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${passage.score}% • " + cueLabels.joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun cueLabelRes(cue: HypeCueCategory): Int = when (cue) {
    HypeCueCategory.PROMOTIONAL_PHRASE -> R.string.echo_cue_promotional
    HypeCueCategory.INTENSIFIER -> R.string.echo_cue_intensifier
    HypeCueCategory.ABSOLUTE_CLAIM -> R.string.echo_cue_absolute
    HypeCueCategory.EXCLAMATION -> R.string.echo_cue_exclamation
}
