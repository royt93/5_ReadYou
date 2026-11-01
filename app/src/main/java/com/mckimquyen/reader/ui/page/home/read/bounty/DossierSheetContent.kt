package com.mckimquyen.reader.ui.page.home.read.bounty

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.mckimquyen.reader.domain.model.bounty.Dossier
import com.mckimquyen.reader.domain.model.bounty.DossierArticleRef
import java.text.DateFormat
import java.time.LocalDate

const val DOSSIER_TAB_TAG_PREFIX = "dossier_tab_"
const val DOSSIER_SECTION_TAG_PREFIX = "dossier_section_"
const val DOSSIER_ARTICLE_TAG_PREFIX = "dossier_article_"
const val DOSSIER_LIMITATION_TAG = "dossier_limitation"
const val DOSSIER_SHARE_TAG = "dossier_share"
const val DOSSIER_CLOSE_TAG = "dossier_close"

private const val DOSSIER_MAX_LIST_HEIGHT_DP = 360
private const val MIN_TOUCH_TARGET_DP = 48

private enum class DossierTab(val key: String) {
    SUMMARY("summary"),
    TIMELINE("timeline"),
    NAMES("names"),
    SOURCES("sources"),
}

/**
 * BOUNTY-02: four-tab view of a local [Dossier]. Stateless apart from the selected tab; callbacks make it testable.
 * It is a mechanical digest of the saved archive and says so (limitation line, names note).
 */
@Composable
fun DossierSheetContent(
    dossier: Dossier,
    onOpenArticle: (String) -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(0) }
    val tabs = DossierTab.values()
    val tabLabels = listOf(
        stringResource(R.string.dossier_tab_summary),
        stringResource(R.string.dossier_tab_timeline),
        stringResource(R.string.dossier_tab_names),
        stringResource(R.string.dossier_tab_sources),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "📁 " + stringResource(R.string.dossier_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                    .testTag(DOSSIER_CLOSE_TAG),
            ) { Text(stringResource(R.string.close)) }
        }
        Text(
            text = dossier.query,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.dossier_limitation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .testTag(DOSSIER_LIMITATION_TAG),
        )
        Spacer(Modifier.height(4.dp))

        TabRow(selectedTabIndex = selected) {
            tabs.forEachIndexed { index, tab ->
                val label = tabLabels[index]
                Tab(
                    selected = selected == index,
                    onClick = { selected = index },
                    modifier = Modifier
                        .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                        .semantics { contentDescription = label }
                        .testTag(DOSSIER_TAB_TAG_PREFIX + tab.key),
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        when (tabs[selected.coerceIn(tabs.indices)]) {
            DossierTab.SUMMARY -> SummarySection(dossier, onOpenArticle)
            DossierTab.TIMELINE -> TimelineSection(dossier, onOpenArticle)
            DossierTab.NAMES -> NamesSection(dossier)
            DossierTab.SOURCES -> SourcesSection(dossier, onOpenArticle)
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onShare,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                .testTag(DOSSIER_SHARE_TAG),
        ) { Text(stringResource(R.string.dossier_share_action)) }
    }
}

@Composable
private fun SectionList(tab: DossierTab, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = DOSSIER_MAX_LIST_HEIGHT_DP.dp)
            .testTag(DOSSIER_SECTION_TAG_PREFIX + tab.key),
        content = content,
    )
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SummarySection(dossier: Dossier, onOpenArticle: (String) -> Unit) {
    val format = rememberDateFormat()
    val empty = stringResource(R.string.dossier_summary_empty)
    SectionList(DossierTab.SUMMARY) {
        if (dossier.summary.isEmpty()) item { EmptyMessage(empty) }
        items(dossier.summary, key = { it.articleId + it.text }) { quote ->
            ArticleRow(
                articleId = quote.articleId,
                title = "“${quote.text}”",
                subtitle = "${quote.feedName} • ${format.format(quote.date)}",
                onOpenArticle = onOpenArticle,
            )
        }
    }
}

@Composable
private fun TimelineSection(dossier: Dossier, onOpenArticle: (String) -> Unit) {
    val format = rememberDateFormat()
    SectionList(DossierTab.TIMELINE) {
        dossier.timeline.forEach { entry ->
            item(key = "day-${entry.day}") { DayHeader(entry.day) }
            items(entry.articles, key = { "${entry.day}-${it.articleId}" }) { ref ->
                ArticleRow(ref.articleId, ref.title, "${ref.feedName} • ${format.format(ref.date)}", onOpenArticle)
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate) {
    Text(
        text = day.toString(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun NamesSection(dossier: Dossier) {
    val note = stringResource(R.string.dossier_names_note)
    val empty = stringResource(R.string.dossier_names_empty)
    val counts = dossier.phrases.map { it to stringResource(R.string.dossier_article_count, it.articleCount) }
    SectionList(DossierTab.NAMES) {
        item {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (counts.isEmpty()) item { EmptyMessage(empty) }
        items(counts, key = { it.first.phrase }) { (phrase, countLabel) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                    .padding(vertical = 8.dp),
            ) {
                Text(phrase.phrase, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(countLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SourcesSection(dossier: Dossier, onOpenArticle: (String) -> Unit) {
    val format = rememberDateFormat()
    val note = stringResource(R.string.dossier_sources_note)
    val single = stringResource(R.string.dossier_sources_single)
    SectionList(DossierTab.SOURCES) {
        if (dossier.framings.isEmpty()) {
            item { EmptyMessage(single) }
        } else {
            item {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        dossier.framings.forEach { framing ->
            item(key = "feed-${framing.feedName}") {
                Text(
                    text = framing.feedName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(framing.headlines, key = { "${framing.feedName}-${it.articleId}" }) { ref: DossierArticleRef ->
                ArticleRow(ref.articleId, ref.title, format.format(ref.date), onOpenArticle)
            }
        }
    }
}

@Composable
private fun ArticleRow(
    articleId: String,
    title: String,
    subtitle: String,
    onOpenArticle: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
            .clickable { onOpenArticle(articleId) }
            .padding(vertical = 8.dp)
            .testTag(DOSSIER_ARTICLE_TAG_PREFIX + articleId),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun rememberDateFormat(): DateFormat {
    val context = LocalContext.current
    return DateFormat.getDateInstance(DateFormat.MEDIUM, context.resources.configuration.locales[0])
}
