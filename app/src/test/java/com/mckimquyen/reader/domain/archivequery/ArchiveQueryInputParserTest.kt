package com.mckimquyen.reader.domain.archivequery

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ArchiveQueryInputParserTest {

    private val utc = ZoneId.of("UTC")
    private val nov2026 = Instant.parse("2026-11-07T10:00:00Z")

    // ── blank / oversized ──

    @Test
    fun `blank input is invalid`() {
        val result = ArchiveQueryInputParser.parse("   ", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Invalid)
        assertEquals(InputError.Blank, (result as ArchiveQueryInputResult.Invalid).reason)
    }

    @Test
    fun `input over 500 chars is invalid`() {
        val long = "a".repeat(501)
        val result = ArchiveQueryInputParser.parse(long, nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Invalid)
        assertEquals(InputError.TooLong, (result as ArchiveQueryInputResult.Invalid).reason)
    }

    // ── no temporal clause → default range ──

    @Test
    fun `plain topic without date uses null range`() {
        val result = ArchiveQueryInputParser.parse("lãi suất ngân hàng", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Valid)
        val parsed = (result as ArchiveQueryInputResult.Valid).value
        assertEquals("lãi suất ngân hàng", parsed.topic)
        assertNull(parsed.explicitRange)
        assertFalse(parsed.needsDateClarification)
    }

    // ── VI temporal grammar ──

    @Test
    fun `VI 'tu thang M den gio' extracts month and topic`() {
        val result = ArchiveQueryInputParser.parse("lãi suất từ tháng 1 đến giờ", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Valid)
        val parsed = (result as ArchiveQueryInputResult.Valid).value
        assertEquals("lãi suất", parsed.topic.trim())
        assertNotNull(parsed.explicitRange)
        // tháng 1 without year → January 2026 (most recent not-future)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), parsed.explicitRange!!.startInclusive)
        assertEquals(nov2026, parsed.explicitRange!!.endExclusive)
    }

    @Test
    fun `VI 'tu thang M nam YYYY den nay'`() {
        val result = ArchiveQueryInputParser.parse("quan điểm về AI từ tháng 3 năm 2026 đến nay", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Valid)
        val parsed = (result as ArchiveQueryInputResult.Valid).value
        assertEquals("quan điểm về AI", parsed.topic.trim())
        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), parsed.explicitRange!!.startInclusive)
    }

    @Test
    fun `VI month without year resolves to most recent not-future occurrence`() {
        // Query in November 2026; "từ tháng 12 đến giờ" → December 2025
        val result = ArchiveQueryInputParser.parse("crypto từ tháng 12 đến giờ", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Valid)
        val parsed = (result as ArchiveQueryInputResult.Valid).value
        assertEquals(Instant.parse("2025-12-01T00:00:00Z"), parsed.explicitRange!!.startInclusive)
    }

    // ── EN temporal grammar ──

    @Test
    fun `EN 'since January' extracts month and topic`() {
        val result = ArchiveQueryInputParser.parse("interest rates since January", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Valid)
        val parsed = (result as ArchiveQueryInputResult.Valid).value
        assertEquals("interest rates", parsed.topic.trim())
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), parsed.explicitRange!!.startInclusive)
    }

    @Test
    fun `EN 'from March 2026 to now'`() {
        val result = ArchiveQueryInputParser.parse("AI policy from March 2026 to now", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Valid)
        val parsed = (result as ArchiveQueryInputResult.Valid).value
        assertEquals("AI policy", parsed.topic.trim())
        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), parsed.explicitRange!!.startInclusive)
    }

    @Test
    fun `EN month name case insensitive`() {
        val result = ArchiveQueryInputParser.parse("stocks since FEBRUARY", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Valid)
        val parsed = (result as ArchiveQueryInputResult.Valid).value
        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), parsed.explicitRange!!.startInclusive)
    }

    @Test
    fun `EN 'since December' in November resolves to prior year`() {
        val result = ArchiveQueryInputParser.parse("crypto since December", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Valid)
        val parsed = (result as ArchiveQueryInputResult.Valid).value
        assertEquals(Instant.parse("2025-12-01T00:00:00Z"), parsed.explicitRange!!.startInclusive)
    }

    // ── edge: empty topic after removing date clause ──

    @Test
    fun `topic empty after date removal is invalid`() {
        val result = ArchiveQueryInputParser.parse("từ tháng 1 đến giờ", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Invalid)
        assertEquals(InputError.BlankTopic, (result as ArchiveQueryInputResult.Invalid).reason)
    }

    // ── range exceeding 12 months requests clarification ──

    @Test
    fun `VI month that produces over 12 month range needs clarification`() {
        // November 2026; "từ tháng 10 năm 2025 đến giờ" = 13 months
        val result = ArchiveQueryInputParser.parse("test từ tháng 10 năm 2025 đến giờ", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Valid)
        val parsed = (result as ArchiveQueryInputResult.Valid).value
        assertTrue(parsed.needsDateClarification)
        assertNull(parsed.explicitRange) // range not applied, needs UI correction
    }

    // ── unrecognized but temporal-looking text ──

    @Test
    fun `unsupported temporal phrase 'last 3 months' requests clarification`() {
        val result = ArchiveQueryInputParser.parse("AI last 3 months", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Valid)
        val parsed = (result as ArchiveQueryInputResult.Valid).value
        assertEquals("AI", parsed.topic.trim())
        assertNull(parsed.explicitRange)
        assertTrue(parsed.needsDateClarification)
    }

    @Test
    fun `unsupported Vietnamese temporal phrase requests clarification`() {
        val result = ArchiveQueryInputParser.parse("lãi suất trong 3 tháng qua", nov2026, utc)
        assertTrue(result is ArchiveQueryInputResult.Valid)
        val parsed = (result as ArchiveQueryInputResult.Valid).value
        assertEquals("lãi suất", parsed.topic.trim())
        assertTrue(parsed.needsDateClarification)
    }
}
