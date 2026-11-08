package com.mckimquyen.reader.domain.archivequery

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class PerspectiveShiftDetectorTest {

    private fun ev(id: String, quote: String) = ArchiveEvidence(
        articleId = id,
        title = "Article $id",
        feedName = "Feed",
        publishedAt = Instant.parse("2026-05-01T00:00:00Z"),
        quote = quote,
        quoteTruncated = false,
        topicMatched = true,
    )

    private fun month(
        year: Int,
        month: Int,
        articleCount: Int = 2,
        evidence: List<ArchiveEvidence> = emptyList(),
    ) = ArchiveMonthWithEvidence(
        month = ArchiveMonth(
            year = year,
            month = month,
            articleCount = articleCount,
            topCandidates = emptyList(),
        ),
        evidence = evidence,
    )

    // ── Happy path: English & Vietnamese ──

    @Test
    fun `adjacent months with increase then decrease detects shift`() {
        val m1 = month(2026, 1, 3, listOf(ev("a1", "The central bank decided to increase interest rates.")))
        val m2 = month(2026, 2, 2, listOf(ev("a2", "The board voted to decrease rates this quarter.")))

        val shift = PerspectiveShiftDetector.detect(m1, m2)

        assertNotNull(shift)
        assertEquals(YearMonth.of(2026, 1), shift!!.earlier)
        assertEquals(YearMonth.of(2026, 2), shift.later)
        assertEquals("increase", shift.firstCue.lowercase())
        assertEquals("decrease", shift.secondCue.lowercase())
        assertEquals("a1", shift.earlierEvidence.articleId)
        assertEquals("a2", shift.laterEvidence.articleId)
    }

    @Test
    fun `reversed direction decrease then increase also detects shift`() {
        val m1 = month(2026, 3, 2, listOf(ev("a1", "Prices are falling across the region.")))
        val m2 = month(2026, 4, 4, listOf(ev("a2", "Prices are rising faster than expected.")))

        val shift = PerspectiveShiftDetector.detect(m1, m2)

        assertNotNull(shift)
        assertEquals("falling", shift!!.firstCue.lowercase())
        assertEquals("rising", shift.secondCue.lowercase())
    }

    @Test
    fun `Vietnamese tang then giam detects shift`() {
        val m1 = month(2026, 5, 2, listOf(ev("a1", "Lãi suất tăng lên mức cao nhất trong năm.")))
        val m2 = month(2026, 6, 3, listOf(ev("a2", "Lãi suất giảm xuống sau quyết định của ngân hàng.")))

        val shift = PerspectiveShiftDetector.detect(m1, m2)

        assertNotNull(shift)
        assertEquals("tăng lên", shift!!.firstCue.lowercase())
        assertEquals("giảm xuống", shift.secondCue.lowercase())
    }

    @Test
    fun `Vietnamese ung ho then phan doi detects shift`() {
        val m1 = month(2026, 7, 2, listOf(ev("a1", "Chính phủ ủng hộ dự án năng lượng mới.")))
        val m2 = month(2026, 8, 2, listOf(ev("a2", "Nhiều chuyên gia phản đối phương án này.")))

        val shift = PerspectiveShiftDetector.detect(m1, m2)

        assertNotNull(shift)
        assertEquals("ủng hộ", shift!!.firstCue.lowercase())
        assertEquals("phản đối", shift.secondCue.lowercase())
    }

    // ── Gap month: must NOT detect shift ──

    @Test
    fun `non-adjacent months with 1-month gap returns null`() {
        val m1 = month(2026, 1, 2, listOf(ev("a1", "Rates increase today.")))
        val m2 = month(2026, 3, 2, listOf(ev("a2", "Rates decrease today."))) // Feb missing

        val shift = PerspectiveShiftDetector.detect(m1, m2)

        assertNull(shift)
    }

    // ── Article count < 2 on either side ──

    @Test
    fun `earlier month with fewer than 2 articles returns null`() {
        val m1 = month(2026, 1, 1, listOf(ev("a1", "Rates increase."))) // count = 1
        val m2 = month(2026, 2, 3, listOf(ev("a2", "Rates decrease.")))

        assertNull(PerspectiveShiftDetector.detect(m1, m2))
    }

    @Test
    fun `later month with fewer than 2 articles returns null`() {
        val m1 = month(2026, 1, 3, listOf(ev("a1", "Rates increase.")))
        val m2 = month(2026, 2, 1, listOf(ev("a2", "Rates decrease."))) // count = 1

        assertNull(PerspectiveShiftDetector.detect(m1, m2))
    }

    // ── Cue in earlier already has the later cue (no new stance) ──

    @Test
    fun `later cue was already present in earlier evidence returns null`() {
        // Month 1 already talked about decrease, so Month 2 decreasing is not a new stance
        val m1 = month(2026, 1, 2, listOf(
            ev("a1", "Some wanted an increase."),
            ev("a2", "Others saw a decrease."),
        ))
        val m2 = month(2026, 2, 2, listOf(ev("a3", "We saw a decrease.")))

        assertNull(PerspectiveShiftDetector.detect(m1, m2))
    }

    // ── Negated sentences ignored ──

    @Test
    fun `direct negation before cue is ignored in English`() {
        // "did not increase" should NOT count as the "increase" cue
        val m1 = month(2026, 1, 2, listOf(ev("a1", "The company did not increase its dividend.")))
        val m2 = month(2026, 2, 2, listOf(ev("a2", "The board chose to decrease payout.")))

        assertNull(PerspectiveShiftDetector.detect(m1, m2))
    }

    @Test
    fun `direct negation before cue is ignored in Vietnamese`() {
        // "không tăng" should NOT count as "tăng"
        val m1 = month(2026, 1, 2, listOf(ev("a1", "Lợi nhuận không tăng trong quý này.")))
        val m2 = month(2026, 2, 2, listOf(ev("a2", "Doanh thu giảm mạnh.")))

        assertNull(PerspectiveShiftDetector.detect(m1, m2))
    }

    // ── Same sentence with both sides of pair ignored ──

    @Test
    fun `sentence with both sides of pair is ignored`() {
        val m1 = month(2026, 1, 2, listOf(ev("a1", "Whether rates increase or decrease remains unknown.")))
        val m2 = month(2026, 2, 2, listOf(ev("a2", "Rates decrease.")))

        assertNull(PerspectiveShiftDetector.detect(m1, m2))
    }

    @Test
    fun `word inside a longer word is not a cue`() {
        val earlier = month(2026, 1, evidence = listOf(ev("a1", "The supporter explained the policy.")))
        val later = month(2026, 2, evidence = listOf(ev("a2", "They oppose the policy.")))
        assertNull(PerspectiveShiftDetector.detect(earlier, later))
    }

    @Test
    fun `negated later evidence does not establish a shift`() {
        for (negation in listOf("not", "never", "no")) {
            val earlier = month(2026, 1, evidence = listOf(ev("a1", "They support the policy.")))
            val later = month(2026, 2, evidence = listOf(ev("a2", "They $negation oppose the policy.")))
            assertNull(negation, PerspectiveShiftDetector.detect(earlier, later))
        }
        for (negation in listOf("không", "chưa", "chẳng")) {
            val earlier = month(2026, 1, evidence = listOf(ev("a1", "Lãi suất tăng lên.")))
            val later = month(2026, 2, evidence = listOf(ev("a2", "Lãi suất $negation giảm xuống.")))
            assertNull(negation, PerspectiveShiftDetector.detect(earlier, later))
        }
    }

    @Test
    fun `adjacent December and January detect shift`() {
        val earlier = month(2025, 12, evidence = listOf(ev("a1", "They approve the proposal.")))
        val later = month(2026, 1, evidence = listOf(ev("a2", "They reject the proposal.")))
        assertNotNull(PerspectiveShiftDetector.detect(earlier, later))
        assertNull(PerspectiveShiftDetector.detect(later, earlier))
        assertNull(PerspectiveShiftDetector.detect(earlier, earlier))
    }

    @Test
    fun `same direction without opposition does not detect a shift`() {
        val earlier = month(2026, 1, evidence = listOf(ev("a1", "They support the proposal.")))
        val later = month(2026, 2, evidence = listOf(ev("a2", "They support the proposal.")))
        assertNull(PerspectiveShiftDetector.detect(earlier, later))
    }

    @Test
    fun `mixed later sentence is not directional evidence`() {
        val earlier = month(2026, 1, evidence = listOf(ev("a1", "They support the proposal.")))
        val later = month(2026, 2, evidence = listOf(ev("a2", "They support or oppose the proposal.")))
        assertNull(PerspectiveShiftDetector.detect(earlier, later))
    }

    @Test
    fun `Vietnamese approval and rejection detect shift`() {
        val earlier = month(2026, 1, evidence = listOf(ev("a1", "Hội đồng chấp thuận đề xuất.")))
        val later = month(2026, 2, evidence = listOf(ev("a2", "Hội đồng bác bỏ đề xuất.")))
        val shift = requireNotNull(PerspectiveShiftDetector.detect(earlier, later))
        assertEquals("chấp thuận", shift.firstCue)
        assertEquals("bác bỏ", shift.secondCue)
    }

    // ── Missing evidence ──

    @Test
    fun `empty evidence on either side returns null`() {
        val m1 = month(2026, 1, 2, emptyList())
        val m2 = month(2026, 2, 2, listOf(ev("a2", "Rates decrease.")))

        assertNull(PerspectiveShiftDetector.detect(m1, m2))
    }
}
