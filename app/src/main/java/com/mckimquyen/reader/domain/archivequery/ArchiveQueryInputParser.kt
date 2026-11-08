package com.mckimquyen.reader.domain.archivequery

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

object ArchiveQueryInputParser {

    private const val MAX_QUERY_CHARS = 500

    // RegexOption.IGNORE_CASE also enables Unicode case folding on the JVM; an inline (?i) alone folds ASCII only,
    // so "TỪ THÁNG" would not match "từ tháng".
    private val CASE_INSENSITIVE = setOf(RegexOption.IGNORE_CASE)

    private val VI_MONTH_PATTERN = Regex(
        """\btừ\s+tháng\s+(\d{1,2})(?:\s+năm\s+(\d{4}))?\s+đến\s+(?:giờ|nay)\b""",
        CASE_INSENSITIVE,
    )

    private val EN_MONTH_NAME_TO_INT: Map<String, Int> = mapOf(
        "january" to 1, "jan" to 1,
        "february" to 2, "feb" to 2,
        "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4,
        "may" to 5,
        "june" to 6, "jun" to 6,
        "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9, "sept" to 9,
        "october" to 10, "oct" to 10,
        "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12,
    )

    private val EN_MONTH_NAMES_OR = EN_MONTH_NAME_TO_INT.keys
        .sortedByDescending { it.length }
        .joinToString("|")

    private val EN_SINCE_PATTERN = Regex(
        """\bsince\s+($EN_MONTH_NAMES_OR)(?:\s+(\d{4}))?\b""",
        CASE_INSENSITIVE,
    )

    private val EN_FROM_PATTERN = Regex(
        """\bfrom\s+($EN_MONTH_NAMES_OR)(?:\s+(\d{4}))?\s+to\s+now\b""",
        CASE_INSENSITIVE,
    )

    // Clearly time-related wording we do not support: ask the user to pick dates instead of defaulting silently.
    private val TEMPORAL_LOOKALIKE_PATTERNS = listOf(
        """\b(?:in\s+the\s+)?last\s+\d+\s+(?:days?|weeks?|months?|years?)\b""",
        """\bpast\s+\d+\s+(?:days?|weeks?|months?|years?)\b""",
        """\b(?:since|from)\s+(?:last\s+year|\d{4})\b""",
        """\b(?:last|this)\s+(?:year|month|week)\b""",
        """\btrong\s+\d+\s+(?:ngày|tuần|tháng|năm)\s+(?:qua|gần\s+đây)\b""",
        """\b\d+\s+(?:tháng|tuần|ngày)\s+(?:qua|gần\s+đây)\b""",
        """\btừ\s+năm\s+\d{4}\b""",
        """\b(?:năm|tháng|tuần)\s+(?:ngoái|nay|trước|này)\b""",
    ).map { Regex(it, CASE_INSENSITIVE) }

    fun parse(text: String, now: Instant, zone: ZoneId): ArchiveQueryInputResult {
        if (text.isBlank()) return ArchiveQueryInputResult.Invalid(InputError.Blank)
        if (text.length > MAX_QUERY_CHARS) return ArchiveQueryInputResult.Invalid(InputError.TooLong)

        val localNow = now.atZone(zone)
        val today = localNow.toLocalDate()
        val currentYm = YearMonth.from(localNow)

        // Try VI grammar
        val viMatch = VI_MONTH_PATTERN.find(text)
        if (viMatch != null) {
            val month = viMatch.groupValues[1].toIntOrNull()
            if (month != null && month in 1..12) {
                val explicitYear = viMatch.groupValues[2].toIntOrNull()
                val startYm = resolveYearMonth(month, explicitYear, currentYm)
                val topic = text.removeRange(viMatch.range).trim()
                return buildResult(topic, startYm, today, now, zone)
            }
        }

        // Try EN "from X [YYYY] to now"
        val enFromMatch = EN_FROM_PATTERN.find(text)
        if (enFromMatch != null) {
            val month = EN_MONTH_NAME_TO_INT[enFromMatch.groupValues[1].lowercase(Locale.ROOT)]
            if (month != null) {
                val explicitYear = enFromMatch.groupValues[2].toIntOrNull()
                val startYm = resolveYearMonth(month, explicitYear, currentYm)
                val topic = text.removeRange(enFromMatch.range).trim()
                return buildResult(topic, startYm, today, now, zone)
            }
        }

        // Try EN "since X [YYYY]"
        val enSinceMatch = EN_SINCE_PATTERN.find(text)
        if (enSinceMatch != null) {
            val month = EN_MONTH_NAME_TO_INT[enSinceMatch.groupValues[1].lowercase(Locale.ROOT)]
            if (month != null) {
                val explicitYear = enSinceMatch.groupValues[2].toIntOrNull()
                val startYm = resolveYearMonth(month, explicitYear, currentYm)
                val topic = text.removeRange(enSinceMatch.range).trim()
                return buildResult(topic, startYm, today, now, zone)
            }
        }

        // Check for unsupported temporal phrases that the user clearly intended as time bounds
        for (pattern in TEMPORAL_LOOKALIKE_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val topic = text.removeRange(match.range).trim()
                if (topic.isBlank()) return ArchiveQueryInputResult.Invalid(InputError.BlankTopic)
                return ArchiveQueryInputResult.Valid(
                    ParsedArchiveQuery(
                        topic = topic,
                        explicitRange = null,
                        needsDateClarification = true,
                    )
                )
            }
        }

        // Plain topic with no recognized temporal phrase
        return ArchiveQueryInputResult.Valid(
            ParsedArchiveQuery(
                topic = text.trim(),
                explicitRange = null,
                needsDateClarification = false,
            )
        )
    }

    private fun resolveYearMonth(month: Int, explicitYear: Int?, current: YearMonth): YearMonth {
        if (explicitYear != null) return YearMonth.of(explicitYear, month)
        // If the month is <= current month, it refers to this year; otherwise it refers to last year.
        return if (month <= current.monthValue) {
            YearMonth.of(current.year, month)
        } else {
            YearMonth.of(current.year - 1, month)
        }
    }

    private fun buildResult(
        topic: String,
        startYm: YearMonth,
        today: LocalDate,
        now: Instant,
        zone: ZoneId,
    ): ArchiveQueryInputResult {
        if (topic.isBlank()) return ArchiveQueryInputResult.Invalid(InputError.BlankTopic)

        val startDate = startYm.atDay(1)
        val validation = ArchiveQueryDateRange.validate(
            start = startDate,
            endInclusive = today,
            now = now,
            zone = zone,
        )

        return when (validation) {
            is RangeValidation.Valid -> ArchiveQueryInputResult.Valid(
                ParsedArchiveQuery(
                    topic = topic,
                    explicitRange = validation.range,
                    needsDateClarification = false,
                )
            )
            is RangeValidation.TooWide,
            is RangeValidation.FutureEnd,
            is RangeValidation.Reversed -> ArchiveQueryInputResult.Valid(
                ParsedArchiveQuery(
                    topic = topic,
                    explicitRange = null,
                    needsDateClarification = true,
                )
            )
        }
    }
}
