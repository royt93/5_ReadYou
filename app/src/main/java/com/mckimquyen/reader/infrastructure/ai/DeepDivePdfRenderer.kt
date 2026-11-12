package com.mckimquyen.reader.infrastructure.ai

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfLayout
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfPlan
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfTexts
import com.mckimquyen.reader.domain.ai.agents.DeepDiveTextMeasurer
import java.io.ByteArrayOutputStream

/** Draws the Deep Dive on one A4 page with the framework PdfDocument; system fonts, no dependency (FRONT-01). */
object DeepDivePdfRenderer {
    private const val ACCENT = 0xFF1F3A5F.toInt()
    private const val GREY = 0xFF555555.toInt()
    private const val RULE_WIDTH = 0.8f
    private const val BULLET = "• "
    private const val COLUMNS = 3

    /** The measurer the view model plans with, so the plan and the drawing agree on heights. */
    val measurer = DeepDiveTextMeasurer { text, sizePt, widthPt, bold ->
        layoutOf(text, sizePt, widthPt, bold, Color.BLACK).height.toFloat()
    }

    private fun layoutOf(text: String, sizePt: Float, widthPt: Float, bold: Boolean, color: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePt
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPt.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
    }

    fun render(plan: DeepDivePdfPlan, title: String, texts: DeepDivePdfTexts): ByteArray {
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(DeepDivePdfLayout.PAGE_WIDTH, DeepDivePdfLayout.PAGE_HEIGHT, 1).create())
            drawPage(page.canvas, plan, title, texts)
            doc.finishPage(page)
            return ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
        } finally {
            doc.close()
        }
    }

    /** Draws top to bottom and stops at the bottom margin: a second page is never started. */
    fun drawPage(canvas: Canvas, plan: DeepDivePdfPlan, title: String, texts: DeepDivePdfTexts) {
        val s = plan.sizePt
        val left = DeepDivePdfLayout.MARGIN.toFloat()
        val width = DeepDivePdfLayout.contentWidth
        val bottom = (DeepDivePdfLayout.PAGE_HEIGHT - DeepDivePdfLayout.MARGIN).toFloat()
        val gap = DeepDivePdfLayout.GAP
        var y = DeepDivePdfLayout.MARGIN.toFloat()

        // Returns false when the block would cross the bottom margin (nothing is drawn then).
        fun block(text: String, size: Float, x: Float, w: Float, bold: Boolean, color: Int = Color.BLACK): Boolean {
            val layout = layoutOf(text, size, w, bold, color)
            if (y + layout.height > bottom) return false
            canvas.save()
            canvas.translate(x, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height
            return true
        }

        if (!block(title, s + DeepDivePdfLayout.TITLE_EXTRA_PT, left, width, true, ACCENT)) return
        y += gap
        if (!block("${texts.aiLabel} · ${texts.dateText}", s + DeepDivePdfLayout.SMALL_EXTRA_PT, left, width, false, GREY)) return
        y += gap
        val rule = Paint().apply { color = ACCENT; strokeWidth = RULE_WIDTH }
        canvas.drawLine(left, y, left + width, y, rule)
        y += gap

        fun heading(text: String): Boolean =
            block(text, s + DeepDivePdfLayout.HEADING_EXTRA_PT, left, width, true, ACCENT).also { if (it) y += gap }

        if (!heading(texts.headingContext)) return
        for (point in plan.report.context) {
            if (!block(BULLET + point, s, left, width, false)) return
            y += gap / 2
        }
        y += gap
        if (!heading(texts.headingCritical)) return
        for (p in plan.report.perspectives) {
            if (!block("$BULLET${p.school}: ${p.view}", s, left, width, false)) return
            y += gap / 2
        }
        y += gap
        if (!heading(texts.headingImpact)) return
        val col = width / COLUMNS
        fun row(a: String, b: String, c: String, bold: Boolean): Boolean {
            val cells = listOf(a, b, c).map { layoutOf(it, s, col - gap, bold, Color.BLACK) }
            val h = cells.maxOf { it.height }.toFloat()
            if (y + h > bottom) return false
            cells.forEachIndexed { i, l ->
                canvas.save()
                canvas.translate(left + i * col, y)
                l.draw(canvas)
                canvas.restore()
            }
            y += h + gap / 2
            return true
        }
        if (!row(texts.colArea, texts.colShort, texts.colLong, true)) return
        for (r in plan.report.impacts) if (!row(r.area, r.shortTerm, r.longTerm, false)) return
        if (plan.truncated) {
            y += gap
            block(texts.truncatedNote, s + DeepDivePdfLayout.SMALL_EXTRA_PT, left, width, false, GREY)
        }
    }
}
