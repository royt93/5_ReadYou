package com.mckimquyen.reader.domain.archivequery

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Closed-open date range: [startInclusive, endExclusive). */
data class ArchiveQueryRange(
    val startInclusive: Instant,
    val endExclusive: Instant,
)

data class ParsedArchiveQuery(
    val topic: String,
    val explicitRange: ArchiveQueryRange?,
    /** True when the user wrote a temporal clause we recognized but the resulting range is invalid (e.g. >12 months). */
    val needsDateClarification: Boolean,
)

sealed interface ArchiveQueryInputResult {
    data class Valid(val value: ParsedArchiveQuery) : ArchiveQueryInputResult
    data class Invalid(val reason: InputError) : ArchiveQueryInputResult
}

enum class InputError { Blank, TooLong, BlankTopic }

sealed interface RangeValidation {
    data class Valid(val range: ArchiveQueryRange) : RangeValidation
    data object Reversed : RangeValidation
    data object FutureEnd : RangeValidation
    data object TooWide : RangeValidation
}

/** An article that passed semantic ranking for the user's query. */
data class RankedArchiveArticle(
    val articleId: String,
    val date: Instant,
    val title: String,
    val feedName: String,
    val score: Float,
    val shortDescription: String,
)

/** One calendar month bucket of ranked articles. */
data class ArchiveMonth(
    val year: Int,
    val month: Int,
    val articleCount: Int,
    val topCandidates: List<RankedArchiveArticle>,
)

/** Final domain result for a temporal archive search. */
data class ArchiveQueryResult(
    val range: ArchiveQueryRange,
    /** Months in chronological order (oldest first) covering every month in the range. */
    val months: List<ArchiveMonthWithEvidence>,
    /** Possible shifts detected between consecutive non-empty months. */
    val shifts: List<ArchivePerspectiveShift>,
    /** Total unique relevant articles found across the entire range. */
    val totalRelevantArticles: Int,
    /** Selected articles whose stored body exceeded the size cap and was therefore not read. */
    val oversizedArticleIds: List<String> = emptyList(),
)
