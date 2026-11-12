package com.mckimquyen.reader.domain.ai.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

// org.json is a stub on the plain JVM, so this pure-logic test runs under Robolectric like the Deep Dive ones.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OracleClaimPolicyParseTest {

    // 2026-11-12T10:00:00Z
    private val now = LocalDate.of(2026, 11, 12).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 10 * 3_600_000L

    private fun endOfDay(date: String): Long =
        LocalDate.parse(date).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1

    private fun json(
        question: String = "Will the bank cut rates by 2026-12-31?",
        on: String = "2026-12-31",
        criteria: String = "The bank's own press release.",
    ) = """{"hasClaim":true,"question":"$question","expiresOn":"$on","resolutionCriteria":"$criteria"}"""

    private fun parse(raw: String) = OracleClaimPolicy.parseAndValidate(raw, now)

    private fun rejected(raw: String): OracleRejection = (parse(raw) as OracleParse.Rejected).reason

    @Test
    fun validClaim_isAccepted_withEndOfDayUtc() {
        val c = parse(json()) as OracleParse.Claim
        assertEquals("Will the bank cut rates by 2026-12-31?", c.question)
        assertEquals(endOfDay("2026-12-31"), c.expiresAt)
        assertEquals("The bank's own press release.", c.resolutionCriteria)
    }

    @Test
    fun hasClaimFalse_isNoClaim() {
        assertEquals(OracleParse.NoClaim, parse("""{"hasClaim":false}"""))
    }

    @Test
    fun markdownFence_isStripped() {
        assertTrue(parse("```json\n${json()}\n```") is OracleParse.Claim)
    }

    @Test
    fun notJson_isRejected() {
        assertEquals(OracleRejection.NOT_JSON, rejected("Sure! Here you go"))
        assertEquals(OracleRejection.NOT_JSON, rejected(""))
    }

    @Test
    fun missingHasClaim_orNonBoolean_isMissingField() {
        assertEquals(OracleRejection.MISSING_FIELD, rejected("""{"question":"Q?"}"""))
        assertEquals(OracleRejection.MISSING_FIELD, rejected("""{"hasClaim":"yes"}"""))
    }

    @Test
    fun claimWithAMissingField_isMissingField() {
        assertEquals(OracleRejection.MISSING_FIELD, rejected("""{"hasClaim":true,"expiresOn":"2026-12-31","resolutionCriteria":"c"}"""))
        assertEquals(OracleRejection.MISSING_FIELD, rejected("""{"hasClaim":true,"question":"Q?","resolutionCriteria":"c"}"""))
        assertEquals(OracleRejection.MISSING_FIELD, rejected("""{"hasClaim":true,"question":"Q?","expiresOn":"2026-12-31"}"""))
    }

    @Test
    fun question_mustBeYesNoShaped_endingInAQuestionMark() {
        assertEquals(OracleRejection.QUESTION_NOT_YES_NO, rejected(json(question = "The bank will cut rates")))
        assertEquals(OracleRejection.QUESTION_NOT_YES_NO, rejected(json(question = "   ")))
        assertTrue(parse(json(question = "Will it launch？")) is OracleParse.Claim)
    }

    @Test
    fun question_lengthBoundary_is160() {
        val ok = "a".repeat(159) + "?"
        val tooLong = "a".repeat(160) + "?"
        assertEquals(160, ok.length)
        assertTrue(parse(json(question = ok)) is OracleParse.Claim)
        assertEquals(OracleRejection.QUESTION_TOO_LONG, rejected(json(question = tooLong)))
    }

    @Test
    fun criteria_emptyAndLengthBoundary_is240() {
        assertEquals(OracleRejection.CRITERIA_EMPTY, rejected(json(criteria = "  ")))
        assertTrue(parse(json(criteria = "c".repeat(240))) is OracleParse.Claim)
        assertEquals(OracleRejection.CRITERIA_TOO_LONG, rejected(json(criteria = "c".repeat(241))))
    }

    @Test
    fun date_badFormat_isBadDate() {
        assertEquals(OracleRejection.BAD_DATE, rejected(json(on = "next month")))
        assertEquals(OracleRejection.BAD_DATE, rejected(json(on = "31/12/2026")))
        assertEquals(OracleRejection.BAD_DATE, rejected(json(on = "2026-02-30")))
    }

    @Test
    fun date_pastIsRejected_todayIsAccepted() {
        assertEquals(OracleRejection.DATE_IN_PAST, rejected(json(on = "2026-11-11")))
        val today = parse(json(on = "2026-11-12")) as OracleParse.Claim
        assertEquals(endOfDay("2026-11-12"), today.expiresAt)
        assertTrue(today.expiresAt > now)
    }

    @Test
    fun date_horizonBoundary_isThreeYears() {
        assertTrue(parse(json(on = "2029-11-12")) is OracleParse.Claim)
        assertEquals(OracleRejection.DATE_TOO_FAR, rejected(json(on = "2029-11-13")))
    }

    @Test
    fun text_isTidied() {
        val c = parse(json(question = "  Will   it\\nlaunch?  ", criteria = "  see   the\\nnotice ")) as OracleParse.Claim
        assertEquals("Will it launch?", c.question)
        assertEquals("see the notice", c.resolutionCriteria)
    }
}
