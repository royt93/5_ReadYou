package com.mckimquyen.reader.domain.ai.agents

fun interface DeepDiveTextMeasurer {
    /** Height in points of [text] wrapped to [widthPt] at [sizePt]. */
    fun height(text: String, sizePt: Float, widthPt: Float, bold: Boolean): Float
}

data class DeepDivePdfTexts(
    val aiLabel: String,
    val headingContext: String,
    val headingCritical: String,
    val headingImpact: String,
    val colArea: String,
    val colShort: String,
    val colLong: String,
    val truncatedNote: String,
    val dateText: String,
)

data class DeepDivePdfPlan(val sizePt: Float, val report: DeepDiveReport, val truncated: Boolean)

/** Pure page planning for the one-page PDF: picks a size and trims from the end until the report fits A4 (FRONT-01). */
object DeepDivePdfLayout {
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    const val MARGIN = 36
    const val GAP = 6f
    const val BULLET_INDENT = 12f
    const val TITLE_EXTRA_PT = 4f
    const val HEADING_EXTRA_PT = 1f
    const val SMALL_EXTRA_PT = -1f
    const val MIN_IMPACT_ROWS = 2
    const val MIN_CONTEXT_POINTS = 3
    const val MIN_PERSPECTIVES = 2
    private const val IMPACT_COLUMNS = 3
    val SIZE_TIERS = listOf(10f, 9f)

    private val availableHeight get() = (PAGE_HEIGHT - 2 * MARGIN).toFloat()
    val contentWidth get() = (PAGE_WIDTH - 2 * MARGIN).toFloat()

    fun plan(report: DeepDiveReport, title: String, texts: DeepDivePdfTexts, measurer: DeepDiveTextMeasurer): DeepDivePdfPlan {
        for (size in SIZE_TIERS) {
            val candidate = DeepDivePdfPlan(size, report, false)
            if (fits(candidate, title, texts, measurer)) return candidate
        }
        val size = SIZE_TIERS.last()
        var current = report
        while (true) {
            val plan = DeepDivePdfPlan(size, current, true)
            if (fits(plan, title, texts, measurer)) return plan
            current = when {
                current.impacts.size > MIN_IMPACT_ROWS -> current.copy(impacts = current.impacts.dropLast(1))
                current.context.size > MIN_CONTEXT_POINTS -> current.copy(context = current.context.dropLast(1))
                current.perspectives.size > MIN_PERSPECTIVES -> current.copy(perspectives = current.perspectives.dropLast(1))
                else -> return plan // Nothing left to drop; the renderer stops at the bottom margin and never adds a page.
            }
        }
    }

    private fun fits(plan: DeepDivePdfPlan, title: String, texts: DeepDivePdfTexts, measurer: DeepDiveTextMeasurer): Boolean =
        contentHeight(plan, title, texts, measurer) <= availableHeight

    fun contentHeight(plan: DeepDivePdfPlan, title: String, texts: DeepDivePdfTexts, measurer: DeepDiveTextMeasurer): Float {
        val s = plan.sizePt
        val w = contentWidth
        var h = measurer.height(title, s + TITLE_EXTRA_PT, w, true) + GAP
        h += measurer.height("${texts.aiLabel} · ${texts.dateText}", s + SMALL_EXTRA_PT, w, false) + GAP * 2
        h += measurer.height(texts.headingContext, s + HEADING_EXTRA_PT, w, true) + GAP
        plan.report.context.forEach { h += measurer.height(it, s, w - BULLET_INDENT, false) + GAP / 2 }
        h += GAP + measurer.height(texts.headingCritical, s + HEADING_EXTRA_PT, w, true) + GAP
        plan.report.perspectives.forEach { h += measurer.height("${it.school}: ${it.view}", s, w - BULLET_INDENT, false) + GAP / 2 }
        h += GAP + measurer.height(texts.headingImpact, s + HEADING_EXTRA_PT, w, true) + GAP
        val col = w / IMPACT_COLUMNS
        h += listOf(texts.colArea, texts.colShort, texts.colLong).maxOf { measurer.height(it, s, col, true) } + GAP / 2
        plan.report.impacts.forEach { row ->
            h += listOf(row.area, row.shortTerm, row.longTerm).maxOf { measurer.height(it, s, col, false) } + GAP / 2
        }
        if (plan.truncated) h += GAP + measurer.height(texts.truncatedNote, s + SMALL_EXTRA_PT, w, false)
        return h
    }
}
