package com.mckimquyen.reader.ui.page.home.read.steelman

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.steelman.ContrastingArticle
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
import java.text.DateFormat

const val ECHO_CONTRAST_CARD_TAG = "echo_contrast_card"
const val ECHO_CONTRAST_CHIP_PREFIX = "echo_contrast_chip_"
const val ECHO_CONTRAST_ROW_PREFIX = "echo_contrast_row_"

private const val MIN_TOUCH_TARGET_DP = 48

@Composable
fun ContrastingCoverageSection(
    state: ContrastingCoverageState,
    enabled: Boolean,
    onSelectPhrase: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val ready = state as? ContrastingCoverageState.Ready ?: return
    ContrastingCoverageCard(
        state = ready,
        onSelectPhrase = onSelectPhrase,
        onOpenArticle = onOpenArticle,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ContrastingCoverageCard(
    state: ContrastingCoverageState.Ready,
    onSelectPhrase: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardDesc = stringResource(R.string.echo_contrast_card_desc, state.items.size)
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM, context.resources.configuration.locales[0]) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDesc }
            .testTag(ECHO_CONTRAST_CARD_TAG),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "⚖️ " + stringResource(R.string.echo_contrast_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.echo_contrast_limitation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (state.phrases.size > 1) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.phrases.forEach { phrase ->
                        val chipDesc = stringResource(R.string.echo_contrast_switch_phrase_desc, phrase)
                        FilterChip(
                            selected = phrase == state.selectedPhrase,
                            onClick = { onSelectPhrase(phrase) },
                            label = { Text(phrase) },
                            modifier = Modifier
                                .semantics { contentDescription = chipDesc }
                                .testTag("$ECHO_CONTRAST_CHIP_PREFIX$phrase"),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            state.items.forEach { item ->
                ContrastingArticleRow(
                    item = item,
                    dateFormat = dateFormat,
                    onOpenArticle = onOpenArticle,
                )
            }
        }
    }
}

@Composable
private fun ContrastingArticleRow(
    item: ContrastingArticle,
    dateFormat: DateFormat,
    onOpenArticle: (String) -> Unit,
) {
    val openDesc = stringResource(R.string.echo_contrast_open_article_desc, item.title, item.feedName)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
            .clickable { onOpenArticle(item.articleId) }
            .padding(vertical = 8.dp)
            .semantics { contentDescription = openDesc }
            .testTag("$ECHO_CONTRAST_ROW_PREFIX${item.articleId}"),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${item.feedName} • ${dateFormat.format(item.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = item.cue,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
