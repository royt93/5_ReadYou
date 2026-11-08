package com.mckimquyen.reader.ui.page.archivequery

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertCountEquals
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.archivequery.ArchiveEvidence
import com.mckimquyen.reader.domain.archivequery.ArchiveMonth
import com.mckimquyen.reader.domain.archivequery.ArchiveMonthWithEvidence
import com.mckimquyen.reader.domain.archivequery.ArchivePerspectiveShift
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryRange
import com.mckimquyen.reader.domain.archivequery.ArchiveQueryResult
import com.mckimquyen.reader.domain.archivequery.RankedArchiveArticle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArchiveQueryPageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val range = ArchiveQueryRange(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-03-31T00:00:00Z"))

    private data class Submit(val text: String, val start: LocalDate?, val end: LocalDate?)

    private val submits = mutableListOf<Submit>()
    private val opened = mutableListOf<String>()
    private var back = 0
    private var retries = 0

    private fun show(state: ArchiveQueryUiState) = compose.setContent {
        ArchiveQueryPage(
            state = state,
            onBack = { back++ },
            onSubmit = { text, start, end -> submits += Submit(text, start, end) },
            onRetry = { retries++ },
            onOpenArticle = { opened += it },
        )
    }

    private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private fun evidence(id: String, quote: String, truncated: Boolean = false, topicMatched: Boolean = true) = ArchiveEvidence(
        articleId = id, title = "Title $id", feedName = "Feed", publishedAt = Instant.parse("2026-02-10T00:00:00Z"),
        quote = quote, quoteTruncated = truncated, topicMatched = topicMatched,
    )

    private fun month(
        m: Int,
        count: Int,
        evidence: List<ArchiveEvidence> = emptyList(),
        candidateIds: List<String> = evidence.map { it.articleId },
    ) = ArchiveMonthWithEvidence(
        ArchiveMonth(
            2026, m, count,
            candidateIds.map { id -> RankedArchiveArticle(id, Instant.parse("2026-${m.toString().padStart(2, '0')}-10T00:00:00Z"), "Title $id", "Feed", 0.5f, "") },
        ),
        evidence,
    )

    private fun ready(
        months: List<ArchiveMonthWithEvidence>,
        shifts: List<ArchivePerspectiveShift> = emptyList(),
        oversized: List<String> = emptyList(),
    ) = ArchiveQueryUiState.Ready(
        "interest rates",
        ArchiveQueryResult(range, months, shifts, months.sumOf { it.month.articleCount }, oversized),
    )

    // ── static content and navigation ──

    @Test fun idleShowsPrivacyNoteLimitsAndTitle() {
        show(ArchiveQueryUiState.Idle)
        compose.onNodeWithText(str(R.string.archive_query_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.archive_query_idle)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.archive_query_limits)).assertExists()
    }

    @Test fun backButtonCallsCallback() {
        show(ArchiveQueryUiState.Idle)
        compose.onNodeWithContentDescription(str(R.string.back)).performClick()
        compose.runOnIdle { assertEquals(1, back) }
    }

    // ── input ──

    @Test fun searchSubmitsQuestionWithNoDatesWhenDateFieldsAreEmpty() {
        show(ArchiveQueryUiState.Idle)
        compose.onNodeWithTag(ARCHIVE_QUESTION_TAG).performTextInput("interest rates since March")
        compose.onNodeWithTag(ARCHIVE_SEARCH_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(Submit("interest rates since March", null, null)), submits) }
    }

    @Test fun imeSearchActionSubmitsToo() {
        show(ArchiveQueryUiState.Idle)
        compose.onNodeWithTag(ARCHIVE_QUESTION_TAG).performTextInput("rates")
        compose.onNodeWithTag(ARCHIVE_QUESTION_TAG).performImeAction()
        compose.runOnIdle { assertEquals(1, submits.size) }
    }

    @Test fun validDatesAreParsedAndPassedToTheCallback() {
        show(ArchiveQueryUiState.Idle)
        compose.onNodeWithTag(ARCHIVE_QUESTION_TAG).performTextInput("rates")
        compose.onNodeWithTag(ARCHIVE_START_TAG).performScrollTo().performTextInput("2026-02-01")
        compose.onNodeWithTag(ARCHIVE_END_TAG).performScrollTo().performTextInput("2026-03-31")
        compose.onNodeWithTag(ARCHIVE_SEARCH_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(Submit("rates", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 31))), submits) }
    }

    @Test fun malformedDateShowsFormatErrorAndDoesNotSubmit() {
        show(ArchiveQueryUiState.Idle)
        compose.onNodeWithTag(ARCHIVE_QUESTION_TAG).performTextInput("rates")
        compose.onNodeWithTag(ARCHIVE_START_TAG).performScrollTo().performTextInput("01/02/2026")
        compose.onNodeWithTag(ARCHIVE_END_TAG).performScrollTo().performTextInput("2026-03-31")
        compose.onNodeWithTag(ARCHIVE_SEARCH_TAG).performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.archive_query_problem_bad_date)).assertExists()
        compose.runOnIdle { assertEquals(0, submits.size) }
    }

    @Test fun impossibleCalendarDateIsRejectedAsMalformed() {
        show(ArchiveQueryUiState.Idle)
        compose.onNodeWithTag(ARCHIVE_QUESTION_TAG).performTextInput("rates")
        compose.onNodeWithTag(ARCHIVE_START_TAG).performScrollTo().performTextInput("2026-02-31")
        compose.onNodeWithTag(ARCHIVE_END_TAG).performScrollTo().performTextInput("2026-03-31")
        compose.onNodeWithTag(ARCHIVE_SEARCH_TAG).performScrollTo().performClick()
        compose.onNodeWithText(str(R.string.archive_query_problem_bad_date)).assertExists()
        compose.runOnIdle { assertEquals(0, submits.size) }
    }

    @Test fun onlyOneDateFilledIsPassedThroughForTheViewModelToReject() {
        show(ArchiveQueryUiState.Idle)
        compose.onNodeWithTag(ARCHIVE_QUESTION_TAG).performTextInput("rates")
        compose.onNodeWithTag(ARCHIVE_START_TAG).performScrollTo().performTextInput("2026-02-01")
        compose.onNodeWithTag(ARCHIVE_SEARCH_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(Submit("rates", LocalDate.of(2026, 2, 1), null)), submits) }
    }

    @Test fun questionLongerThanLimitIsCappedInTheField() {
        show(ArchiveQueryUiState.Idle)
        compose.onNodeWithTag(ARCHIVE_QUESTION_TAG).performTextInput("a".repeat(600))
        compose.onNodeWithTag(ARCHIVE_SEARCH_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(500, submits.single().text.length) }
    }

    // ── states ──

    @Test fun searchingShowsProgressAndRangeAndDisablesSearch() {
        show(ArchiveQueryUiState.Searching("interest rates", range))
        compose.onNodeWithText(str(R.string.archive_query_searching)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ARCHIVE_PROGRESS_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ARCHIVE_SEARCH_TAG).assertIsNotEnabled()
    }

    @Test fun invalidInputShowsTheMatchingMessage() {
        val cases = mapOf(
            ArchiveQueryProblem.Blank to R.string.archive_query_problem_blank,
            ArchiveQueryProblem.TooLong to R.string.archive_query_problem_too_long,
            ArchiveQueryProblem.BlankTopic to R.string.archive_query_problem_blank_topic,
            ArchiveQueryProblem.NeedsDateClarification to R.string.archive_query_problem_clarify_dates,
            ArchiveQueryProblem.IncompleteRange to R.string.archive_query_problem_incomplete_range,
            ArchiveQueryProblem.RangeReversed to R.string.archive_query_problem_reversed,
            ArchiveQueryProblem.RangeInFuture to R.string.archive_query_problem_future,
            ArchiveQueryProblem.RangeTooWide to R.string.archive_query_problem_too_wide,
        )
        for ((problem, res) in cases) {
            assertEquals(res, problemMessageRes(problem))
        }
        show(ArchiveQueryUiState.InvalidInput(ArchiveQueryProblem.RangeTooWide))
        compose.onNodeWithText(str(R.string.archive_query_problem_too_wide)).performScrollTo().assertIsDisplayed()
    }

    @Test fun noResultsExplainsAndShowsSearchedRange() {
        show(ArchiveQueryUiState.NoResults("interest rates", range))
        compose.onNodeWithText(str(R.string.archive_query_no_results)).performScrollTo().assertIsDisplayed()
    }

    @Test fun errorShowsMessageAndRetryCallsCallback() {
        show(ArchiveQueryUiState.Error)
        compose.onNodeWithText(str(R.string.archive_query_error)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(ARCHIVE_RETRY_TAG).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    // ── timeline ──

    @Test fun everyMonthIsListedOldestFirstAndEmptyMonthSaysNotEnoughData() {
        show(ready(listOf(month(1, 1, listOf(evidence("a", "January sentence."))), month(2, 0), month(3, 1, listOf(evidence("c", "March sentence."))))))
        compose.onAllNodesWithTag(ARCHIVE_MONTH_TAG).assertCountEquals(3)
        compose.onNodeWithText(str(R.string.archive_query_month_empty)).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText(str(R.string.archive_query_article_count, 1)).assertCountEquals(2)
    }

    @Test fun quoteIsShownVerbatimWithTitleAndOpensTheExactArticle() {
        val quote = "The central bank raised interest rates again."
        show(ready(listOf(month(2, 2, listOf(evidence("art-7", quote))))))
        compose.onNodeWithText("“$quote”").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("${ARCHIVE_OPEN_TAG_PREFIX}art-7").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("art-7"), opened) }
    }

    @Test fun truncatedQuoteAndFallbackQuoteAreLabelledHonestly() {
        show(ready(listOf(month(2, 1, listOf(evidence("a", "Opening line of the article", truncated = true, topicMatched = false))))))
        compose.onNodeWithText(str(R.string.archive_query_quote_truncated)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.archive_query_quote_no_topic_word)).performScrollTo().assertIsDisplayed()
    }

    @Test fun monthWithCountButNoQuoteSaysNoQuoteAvailable() {
        show(ready(listOf(month(2, 3))))
        compose.onNodeWithText(str(R.string.archive_query_no_quote)).performScrollTo().assertIsDisplayed()
    }

    @Test fun oversizedArticleIsReportedWhereItWasSkipped() {
        show(ready(listOf(month(2, 1, candidateIds = listOf("big"))), oversized = listOf("big")))
        compose.onNodeWithText(str(R.string.archive_query_body_too_large)).performScrollTo().assertIsDisplayed()
    }

    @Test fun shiftMarkerSitsBetweenMonthsWithCuesBothQuotesAndDisclaimer() {
        val early = evidence("e", "Analysts expect rates to increase.")
        val late = evidence("l", "The bank will decrease rates.")
        val shift = ArchivePerspectiveShift(YearMonth.of(2026, 1), YearMonth.of(2026, 2), "increase", "decrease", early, late)
        show(ready(listOf(month(1, 2, listOf(early)), month(2, 2, listOf(late))), listOf(shift)))
        compose.onNodeWithTag(ARCHIVE_SHIFT_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.archive_query_shift_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.archive_query_shift_cues, "increase", "decrease")).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.archive_query_shift_disclaimer)).assertIsDisplayed()
    }

    @Test fun noShiftMarkerWhenThereAreNoShifts() {
        show(ready(listOf(month(1, 2, listOf(evidence("e", "Rates rise."))))))
        compose.onAllNodesWithTag(ARCHIVE_SHIFT_TAG).assertCountEquals(0)
    }

    @Test fun limitsAreVisibleAlongsideResults() {
        show(ready(listOf(month(1, 1, listOf(evidence("e", "Rates rise."))))))
        compose.onNodeWithText(str(R.string.archive_query_limits)).performScrollTo().assertExists()
    }

    @Test fun monthTitleUsesALocaleAwareMonthAndYear() {
        show(ready(listOf(month(2, 1, listOf(evidence("e", "Rates rise."))))))
        val title = java.time.Month.of(2).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " 2026"
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
    }
}
