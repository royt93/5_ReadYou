package com.mckimquyen.reader.domain.archivequery

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.YearMonth

class TemporalGrouperTest {

    private fun article(id: String, dateStr: String, score: Float, title: String = "t") =
        RankedArchiveArticle(
            articleId = id,
            date = Instant.parse(dateStr),
            title = title,
            feedName = "feed",
            score = score,
            shortDescription = "",
        )

    // ── empty ──

    @Test
    fun `empty input produces empty map`() {
        val grouper = TemporalGrouper()
        assertTrue(grouper.result().isEmpty())
    }

    // ── single month ──

    @Test
    fun `single article goes into its month`() {
        val grouper = TemporalGrouper()
        grouper.add(YearMonth.of(2026, 3), article("a1", "2026-03-15T10:00:00Z", 0.5f))
        val result = grouper.result()
        assertEquals(1, result.size)
        val month = result[YearMonth.of(2026, 3)]!!
        assertEquals(2026, month.year)
        assertEquals(3, month.month)
        assertEquals(1, month.articleCount)
        assertEquals(1, month.topCandidates.size)
        assertEquals("a1", month.topCandidates[0].articleId)
    }

    // ── multi-month ──

    @Test
    fun `articles in different months go into separate buckets`() {
        val grouper = TemporalGrouper()
        grouper.add(YearMonth.of(2026, 1), article("a1", "2026-01-10T00:00:00Z", 0.8f))
        grouper.add(YearMonth.of(2026, 3), article("a2", "2026-03-20T00:00:00Z", 0.7f))
        val result = grouper.result()
        assertEquals(2, result.size)
        assertTrue(result.containsKey(YearMonth.of(2026, 1)))
        assertTrue(result.containsKey(YearMonth.of(2026, 3)))
    }

    // ── top-3 with score/date/id tie-break ──

    @Test
    fun `keeps only top 3 candidates by score descending`() {
        val grouper = TemporalGrouper()
        val ym = YearMonth.of(2026, 5)
        grouper.add(ym, article("a1", "2026-05-01T00:00:00Z", 0.3f))
        grouper.add(ym, article("a2", "2026-05-02T00:00:00Z", 0.9f))
        grouper.add(ym, article("a3", "2026-05-03T00:00:00Z", 0.5f))
        grouper.add(ym, article("a4", "2026-05-04T00:00:00Z", 0.7f))
        val month = grouper.result()[ym]!!
        assertEquals(4, month.articleCount) // count includes all
        assertEquals(3, month.topCandidates.size) // only top 3 retained
        assertEquals("a2", month.topCandidates[0].articleId) // 0.9
        assertEquals("a4", month.topCandidates[1].articleId) // 0.7
        assertEquals("a3", month.topCandidates[2].articleId) // 0.5
    }

    @Test
    fun `tie-break same score by date descending then id descending`() {
        val grouper = TemporalGrouper()
        val ym = YearMonth.of(2026, 6)
        grouper.add(ym, article("aaa", "2026-06-01T00:00:00Z", 0.5f))
        grouper.add(ym, article("bbb", "2026-06-01T00:00:00Z", 0.5f))
        grouper.add(ym, article("ccc", "2026-06-02T00:00:00Z", 0.5f))
        grouper.add(ym, article("ddd", "2026-06-02T00:00:00Z", 0.5f))
        val candidates = grouper.result()[ym]!!.topCandidates
        assertEquals(3, candidates.size)
        // date desc: ddd/ccc (06-02) before aaa/bbb (06-01)
        // id desc within same date
        assertEquals("ddd", candidates[0].articleId)
        assertEquals("ccc", candidates[1].articleId)
        assertEquals("bbb", candidates[2].articleId)
    }

    // ── duplicate IDs ──

    @Test
    fun `duplicate article ID does not occupy multiple candidate slots`() {
        val grouper = TemporalGrouper()
        val ym = YearMonth.of(2026, 7)
        grouper.add(ym, article("a1", "2026-07-01T00:00:00Z", 0.8f))
        grouper.add(ym, article("a1", "2026-07-01T00:00:00Z", 0.9f))
        grouper.add(ym, article("a2", "2026-07-02T00:00:00Z", 0.7f))
        grouper.add(ym, article("a3", "2026-07-03T00:00:00Z", 0.6f))
        val candidates = grouper.result()[ym]!!.topCandidates
        assertEquals(3, candidates.size)
        assertEquals(3, candidates.map { it.articleId }.toSet().size)
        assertEquals(setOf("a1", "a2", "a3"), candidates.map { it.articleId }.toSet())
    }

    @Test
    fun `duplicate article IDs are deduplicated in count`() {
        val grouper = TemporalGrouper()
        val ym = YearMonth.of(2026, 7)
        grouper.add(ym, article("a1", "2026-07-01T00:00:00Z", 0.8f))
        grouper.add(ym, article("a1", "2026-07-01T00:00:00Z", 0.9f)) // same ID
        grouper.add(ym, article("a2", "2026-07-02T00:00:00Z", 0.7f))
        val month = grouper.result()[ym]!!
        assertEquals(2, month.articleCount) // 2 unique IDs
    }

    // ── year crossing ──

    @Test
    fun `adjacent months across year boundary`() {
        val grouper = TemporalGrouper()
        grouper.add(YearMonth.of(2025, 12), article("a1", "2025-12-15T00:00:00Z", 0.6f))
        grouper.add(YearMonth.of(2026, 1), article("a2", "2026-01-10T00:00:00Z", 0.7f))
        val result = grouper.result()
        assertEquals(2, result.size)
        assertTrue(result.containsKey(YearMonth.of(2025, 12)))
        assertTrue(result.containsKey(YearMonth.of(2026, 1)))
    }

    // ── count preserves total while candidates capped ──

    @Test
    fun `article count includes all unique articles even beyond top 3`() {
        val grouper = TemporalGrouper()
        val ym = YearMonth.of(2026, 8)
        repeat(10) { i ->
            grouper.add(ym, article("a$i", "2026-08-${10 + i}T00:00:00Z", 0.1f * (i + 1)))
        }
        val month = grouper.result()[ym]!!
        assertEquals(10, month.articleCount)
        assertEquals(3, month.topCandidates.size)
    }
}
