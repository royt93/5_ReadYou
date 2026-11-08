package com.mckimquyen.reader.ui.page.archivequery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.archivequery.ArchiveEvidence
import com.mckimquyen.reader.domain.archivequery.ArchiveMonthWithEvidence
import com.mckimquyen.reader.domain.archivequery.ArchivePerspectiveShift
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryRange
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryResult
import com.mckimquyen.reader.ui.component.base.BaseScaffold
import com.mckimquyen.reader.ui.component.base.FeedbackIconButton
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.format.TextStyle
import java.util.Locale

const val ARCHIVE_QUESTION_TAG = "archive_question"
const val ARCHIVE_START_TAG = "archive_start_date"
const val ARCHIVE_END_TAG = "archive_end_date"
const val ARCHIVE_SEARCH_TAG = "archive_search"
const val ARCHIVE_PROGRESS_TAG = "archive_progress"
const val ARCHIVE_RETRY_TAG = "archive_retry"
const val ARCHIVE_MONTH_TAG = "archive_month"
const val ARCHIVE_SHIFT_TAG = "archive_shift"
const val ARCHIVE_OPEN_TAG_PREFIX = "archive_open_"
private const val MAX_QUESTION_CHARS = 500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveQueryPage(
    state: ArchiveQueryUiState,
    onBack: () -> Unit,
    onSubmit: (String, LocalDate?, LocalDate?) -> Unit,
    onRetry: () -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    var question by rememberSaveable { mutableStateOf("") }
    var startText by rememberSaveable { mutableStateOf("") }
    var endText by rememberSaveable { mutableStateOf("") }
    var dateInputInvalid by remember { mutableStateOf(false) }
    val busy = state is ArchiveQueryUiState.Searching

    BaseScaffold(
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.archive_query_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.archive_query_idle), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.archive_query_limits), style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = question,
                onValueChange = { question = it.take(MAX_QUESTION_CHARS) },
                modifier = Modifier.fillMaxWidth().testTag(ARCHIVE_QUESTION_TAG),
                label = { Text(stringResource(R.string.archive_query_question_label)) },
                placeholder = { Text(stringResource(R.string.archive_query_question_hint)) },
                maxLines = 4,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    submit(question, startText, endText, onSubmit) { dateInputInvalid = it }
                }),
            )
            Text(stringResource(R.string.archive_query_dates_optional), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = startText,
                    onValueChange = { startText = it.take(DATE_INPUT_CHARS); dateInputInvalid = false },
                    modifier = Modifier.weight(1f).testTag(ARCHIVE_START_TAG),
                    label = { Text(stringResource(R.string.archive_query_start_date)) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = endText,
                    onValueChange = { endText = it.take(DATE_INPUT_CHARS); dateInputInvalid = false },
                    modifier = Modifier.weight(1f).testTag(ARCHIVE_END_TAG),
                    label = { Text(stringResource(R.string.archive_query_end_date)) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        submit(question, startText, endText, onSubmit) { dateInputInvalid = it }
                    }),
                )
            }
            if (dateInputInvalid) Text(stringResource(R.string.archive_query_problem_bad_date), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = { submit(question, startText, endText, onSubmit) { dateInputInvalid = it } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET).testTag(ARCHIVE_SEARCH_TAG),
            ) { Text(stringResource(R.string.archive_query_search)) }

            when (state) {
                ArchiveQueryUiState.Idle -> Unit
                is ArchiveQueryUiState.InvalidInput -> Text(
                    stringResource(problemMessageRes(state.problem)), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
                is ArchiveQueryUiState.Searching -> {
                    Text(stringResource(R.string.archive_query_searching))
                    Text(formatRange(state.range, ZoneId.systemDefault()), style = MaterialTheme.typography.bodySmall)
                    CircularProgressIndicator(modifier = Modifier.testTag(ARCHIVE_PROGRESS_TAG))
                }
                is ArchiveQueryUiState.NoResults -> {
                    Text(stringResource(R.string.archive_query_no_results))
                    Text(formatRange(state.range, ZoneId.systemDefault()), style = MaterialTheme.typography.bodySmall)
                }
                is ArchiveQueryUiState.Error -> {
                    Text(stringResource(R.string.archive_query_error), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry, modifier = Modifier.testTag(ARCHIVE_RETRY_TAG)) {
                        Text(stringResource(R.string.retry))
                    }
                }
                is ArchiveQueryUiState.Ready -> Timeline(state.result, onOpenArticle)
            }
        }
        },
    )
}

@Composable
private fun Timeline(result: ArchiveQueryResult, onOpenArticle: (String) -> Unit) {
    val zone = ZoneId.systemDefault()
    Text(formatRange(result.range, zone), style = MaterialTheme.typography.bodySmall)
    result.months.forEach { month ->
        result.shifts.firstOrNull { it.later == month.yearMonth }?.let { ShiftCard(it, onOpenArticle) }
        MonthCard(month, result.oversizedArticleIds, zone, onOpenArticle)
    }
}

@Composable
private fun MonthCard(
    month: ArchiveMonthWithEvidence,
    oversizedArticleIds: List<String>,
    zone: ZoneId,
    onOpenArticle: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().testTag(ARCHIVE_MONTH_TAG)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(monthLabel(month, Locale.getDefault()), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.archive_query_article_count, month.month.articleCount), style = MaterialTheme.typography.bodySmall)
            if (month.month.articleCount == 0) {
                Text(stringResource(R.string.archive_query_month_empty))
            } else if (month.evidence.isEmpty()) {
                Text(stringResource(R.string.archive_query_no_quote))
            }
            month.evidence.forEach { evidence -> EvidenceCard(evidence, onOpenArticle) }
            if (month.month.topCandidates.any { it.articleId in oversizedArticleIds }) {
                Text(stringResource(R.string.archive_query_body_too_large), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EvidenceCard(evidence: ArchiveEvidence, onOpenArticle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val quoteText = if (evidence.quoteTruncated) "“${evidence.quote}…”" else "“${evidence.quote}”"
        Text(quoteText, style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic))
        if (evidence.quoteTruncated) Text(stringResource(R.string.archive_query_quote_truncated), style = MaterialTheme.typography.bodySmall)
        if (!evidence.topicMatched) Text(stringResource(R.string.archive_query_quote_no_topic_word), style = MaterialTheme.typography.bodySmall)
        TextButtonForSource(evidence, onOpenArticle)
    }
}

@Composable
private fun TextButtonForSource(evidence: ArchiveEvidence, onOpenArticle: (String) -> Unit) {
    val openLabel = stringResource(R.string.archive_query_open_article, evidence.title)
    OutlinedButton(
        onClick = { onOpenArticle(evidence.articleId) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = openLabel }
            .testTag("$ARCHIVE_OPEN_TAG_PREFIX${evidence.articleId}"),
    ) {
        Text(
            "${evidence.title}\n${evidence.feedName} · ${evidence.publishedAt.atZone(ZoneId.systemDefault()).toLocalDate()}",
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun ShiftCard(shift: ArchivePerspectiveShift, onOpenArticle: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(ARCHIVE_SHIFT_TAG),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.archive_query_shift_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.archive_query_shift_cues, shift.firstCue, shift.secondCue))
            EvidenceCard(shift.earlierEvidence, onOpenArticle)
            EvidenceCard(shift.laterEvidence, onOpenArticle)
            Text(stringResource(R.string.archive_query_shift_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun problemMessageRes(problem: ArchiveQueryProblem): Int = when (problem) {
    ArchiveQueryProblem.Blank -> R.string.archive_query_problem_blank
    ArchiveQueryProblem.TooLong -> R.string.archive_query_problem_too_long
    ArchiveQueryProblem.BlankTopic -> R.string.archive_query_problem_blank_topic
    ArchiveQueryProblem.NeedsDateClarification -> R.string.archive_query_problem_clarify_dates
    ArchiveQueryProblem.IncompleteRange -> R.string.archive_query_problem_incomplete_range
    ArchiveQueryProblem.RangeReversed -> R.string.archive_query_problem_reversed
    ArchiveQueryProblem.RangeInFuture -> R.string.archive_query_problem_future
    ArchiveQueryProblem.RangeTooWide -> R.string.archive_query_problem_too_wide
}

private fun submit(
    question: String,
    startText: String,
    endText: String,
    onSubmit: (String, LocalDate?, LocalDate?) -> Unit,
    onDateError: (Boolean) -> Unit,
) {
    val start = startText.takeIf { it.isNotBlank() }?.let(::parseDate)
    val end = endText.takeIf { it.isNotBlank() }?.let(::parseDate)
    if ((startText.isNotBlank() && start == null) || (endText.isNotBlank() && end == null)) {
        onDateError(true)
        return
    }
    onDateError(false)
    onSubmit(question, start, end)
}

private fun parseDate(text: String): LocalDate? = runCatching {
    LocalDate.parse(text, DATE_FORMAT)
}.getOrNull()

@Composable
private fun formatRange(range: ArchiveQueryRange, zone: ZoneId): String {
    val start = range.startInclusive.atZone(zone).toLocalDate()
    // endExclusive is the first instant NOT searched, so the last searched day is the one just before it.
    val end = range.endExclusive.minusMillis(1).atZone(zone).toLocalDate()
    return stringResource(R.string.archive_query_range_used, start.toString(), end.toString())
}

private fun monthLabel(month: ArchiveMonthWithEvidence, locale: Locale): String {
    val monthName = Month.of(month.month.month).getDisplayName(TextStyle.FULL, locale)
    return "$monthName ${month.month.year}"
}

private const val DATE_INPUT_CHARS = 10
private val MIN_TOUCH_TARGET = 48.dp
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)
