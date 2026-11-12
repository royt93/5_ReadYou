package com.mckimquyen.reader.domain.ai.agents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class DeepDivePdfLayoutTest {

    private val texts = DeepDivePdfTexts("AI", "Context", "Critical", "Impact", "Area", "Short", "Long", "More in the app", "2026-11-11")

    // Fake measurer: every character is 0.5 * size wide, lines are 1.2 * size tall.
    private val measurer = DeepDiveTextMeasurer { text, size, width, _ ->
        val perLine = (width / (size * 0.5f)).toInt().coerceAtLeast(1)
        ceil(text.length.toFloat() / perLine).coerceAtLeast(1f) * size * 1.2f
    }

    private fun report(contextN: Int, perspN: Int, impactN: Int, len: Int = 40) = DeepDiveReport(
        "a",
        (1..contextN).map { "c$it".padEnd(len, 'x') },
        (1..perspN).map { DeepDivePerspective("s$it", "v$it".padEnd(len, 'x')) },
        (1..impactN).map { DeepDiveImpact("a$it", "s$it".padEnd(len, 'x'), "l$it".padEnd(len, 'x')) },
    )

    private fun fits(plan: DeepDivePdfPlan) =
        DeepDivePdfLayout.contentHeight(plan, "Title", texts, measurer) <= DeepDivePdfLayout.PAGE_HEIGHT - 2f * DeepDivePdfLayout.MARGIN

    @Test fun smallReport_keepsEverythingAtTheLargestSize() {
        val plan = DeepDivePdfLayout.plan(report(3, 2, 2), "Title", texts, measurer)
        assertEquals(DeepDivePdfLayout.SIZE_TIERS.first(), plan.sizePt)
        assertFalse(plan.truncated)
        assertEquals(report(3, 2, 2), plan.report)
        assertTrue(fits(plan))
    }

    @Test fun biggerReport_dropsToTheSmallerSizeBeforeDroppingAnything() {
        val candidate = (1..400 step 5).map { report(5, 4, 5, len = it) }.first { r ->
            val first = DeepDivePdfPlan(DeepDivePdfLayout.SIZE_TIERS[0], r, false)
            val second = DeepDivePdfPlan(DeepDivePdfLayout.SIZE_TIERS[1], r, false)
            !fits(first) && fits(second)
        }
        val plan = DeepDivePdfLayout.plan(candidate, "Title", texts, measurer)
        assertEquals(DeepDivePdfLayout.SIZE_TIERS[1], plan.sizePt)
        assertFalse(plan.truncated)
        assertEquals(candidate, plan.report)
    }

    @Test fun hugeReport_isTrimmedFromTheEndAndFlaggedTruncated() {
        val plan = DeepDivePdfLayout.plan(report(5, 4, 5, len = 240), "Title", texts, measurer)
        assertTrue(plan.truncated)
        assertTrue(fits(plan))
        assertTrue(plan.report.impacts.size in 2..5)
        assertTrue(plan.report.context.size in 3..5)
        assertTrue(plan.report.perspectives.size in 2..4)
    }

    @Test fun trimmingKeepsTheFirstItemsAndTheirOrder() {
        val original = report(5, 4, 5, len = 240)
        val plan = DeepDivePdfLayout.plan(original, "Title", texts, measurer)
        assertEquals(original.impacts.take(plan.report.impacts.size), plan.report.impacts)
        assertEquals(original.context.take(plan.report.context.size), plan.report.context)
        assertEquals(original.perspectives.take(plan.report.perspectives.size), plan.report.perspectives)
    }

    @Test fun impactRowsAreTrimmedBeforeContextAndPerspectives() {
        val plan = DeepDivePdfLayout.plan(report(5, 4, 5, len = 240), "Title", texts, measurer)
        if (plan.report.impacts.size > 2) {
            assertEquals("context must be intact while impact rows remain above the minimum", 5, plan.report.context.size)
            assertEquals(4, plan.report.perspectives.size)
        }
    }

    @Test fun neverTrimsBelowTheMinimums() {
        val tiny = DeepDiveTextMeasurer { _, size, _, _ -> size * 400f } // nothing can ever fit
        val plan = DeepDivePdfLayout.plan(report(5, 4, 5), "Title", texts, tiny)
        assertEquals(2, plan.report.impacts.size)
        assertEquals(3, plan.report.context.size)
        assertEquals(2, plan.report.perspectives.size)
        assertTrue(plan.truncated)
    }

    @Test fun pageIsA4InPoints() {
        assertEquals(595, DeepDivePdfLayout.PAGE_WIDTH)
        assertEquals(842, DeepDivePdfLayout.PAGE_HEIGHT)
    }
}
