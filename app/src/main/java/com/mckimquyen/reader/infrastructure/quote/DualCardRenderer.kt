package com.mckimquyen.reader.infrastructure.quote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import com.mckimquyen.reader.domain.steelman.DualPerspectiveSpec
import com.mckimquyen.reader.domain.steelman.DualPerspectiveTruncator

/** Draws the 1080x1920 two-headline card with android.graphics only; pure, needs no Context. */
object DualCardRenderer {
    const val CARD_WIDTH = 1080
    const val CARD_HEIGHT = 1920
    const val WATERMARK = "RSS Cat Hub"
    const val DISCLAIMER = "Two headlines from my reading archive. Not a fact-check."

    private const val HALF_HEIGHT = CARD_HEIGHT / 2
    private const val MARGIN = 90
    private const val CONTENT_WIDTH = CARD_WIDTH - 2 * MARGIN
    private const val LABEL_TOP_OFFSET = 110f
    private const val HEADLINE_TOP_OFFSET = 220f
    private const val META_BOTTOM_OFFSET = 190f
    private const val FOOTER_ZONE = 220f
    private const val META_TO_CUE_GAP = 1.4f
    private const val LABEL_SIZE = 36f
    private const val HEADLINE_SIZE = 66f
    private const val META_SIZE = 38f
    private const val CUE_SIZE = 34f
    private const val LINE_SPACING_MULT = 1.2f
    private const val MAX_HEADLINE_LINES = 7
    // The lower half also holds source/date, wording cue and the footer, so its headline gets fewer lines.
    private const val MAX_LOWER_HEADLINE_LINES = 4
    private const val FOOTER_BASELINE_OFFSET = 70f
    private const val DISCLAIMER_SIZE = 30f
    private const val WATERMARK_SIZE = 30f
    private const val DISCLAIMER_LINES = 2
    private const val DISCLAIMER_TOP_OFFSET = 150f

    private const val TOP_BG = 0xFF0B0B0F.toInt()
    private const val TOP_TEXT = 0xFFF5F5F5.toInt()
    private const val TOP_ACCENT = 0xFF39FF14.toInt()
    private const val TOP_MUTED = 0xFF9A9AA5.toInt()
    private const val BOTTOM_BG = 0xFFFFFFFF.toInt()
    private const val BOTTOM_TEXT = 0xFF14141A.toInt()
    private const val BOTTOM_ACCENT = 0xFFE5173F.toInt()
    private const val BOTTOM_MUTED = 0xFF6B6B75.toInt()

    private const val LABEL_TOP = "NOW READING"
    private const val LABEL_BOTTOM = "ANOTHER SAVED SOURCE"
    private const val CUE_PREFIX = "wording: "

    private data class Palette(val bg: Int, val text: Int, val accent: Int, val muted: Int)

    private val TOP = Palette(TOP_BG, TOP_TEXT, TOP_ACCENT, TOP_MUTED)
    private val BOTTOM = Palette(BOTTOM_BG, BOTTOM_TEXT, BOTTOM_ACCENT, BOTTOM_MUTED)

    fun render(spec: DualPerspectiveSpec): Bitmap = draw(spec, null)

    /** Test hook: renders the card and also returns the rectangle of every text block that was drawn. */
    internal fun renderWithBounds(spec: DualPerspectiveSpec): Pair<Bitmap, List<RectF>> {
        val bounds = mutableListOf<RectF>()
        return draw(spec, bounds) to bounds
    }

    private fun draw(spec: DualPerspectiveSpec, bounds: MutableList<RectF>?): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawHalf(canvas, 0f, TOP, LABEL_TOP, spec.topTitle, "${spec.topSource} · ${spec.topDate}", null, MAX_HEADLINE_LINES, DualPerspectiveTruncator.MAX_HEADLINE_CHARS, bounds)
        drawHalf(
            canvas, HALF_HEIGHT.toFloat(), BOTTOM, LABEL_BOTTOM, spec.bottomTitle,
            "${spec.bottomSource} · ${spec.bottomDate}", spec.cue.takeIf { it.isNotBlank() }, MAX_LOWER_HEADLINE_LINES, DualPerspectiveTruncator.MAX_LOWER_HEADLINE_CHARS, bounds,
        )
        drawFooter(canvas, bounds)
        return bitmap
    }

    private fun drawHalf(canvas: Canvas, top: Float, p: Palette, label: String, title: String, meta: String, cue: String?, maxLines: Int, maxChars: Int, bounds: MutableList<RectF>?) {
        val fill = Paint().apply { color = p.bg }
        canvas.drawRect(0f, top, CARD_WIDTH.toFloat(), top + HALF_HEIGHT, fill)
        drawLine(canvas, label, p.accent, LABEL_SIZE, true, top + LABEL_TOP_OFFSET, bounds)

        val text = DualPerspectiveTruncator.truncate(title, maxChars).text
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.text
            textSize = HEADLINE_SIZE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING_MULT)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(MARGIN.toFloat(), top + HEADLINE_TOP_OFFSET)
        layout.draw(canvas)
        canvas.restore()
        bounds?.add(blockBounds(layout, top + HEADLINE_TOP_OFFSET))

        // The lower half must not run into the footer: keep meta + cue entirely above FOOTER_ZONE.
        val footerTop = CARD_HEIGHT - FOOTER_ZONE
        val blockHeight = META_SIZE * META_TO_CUE_GAP + CUE_SIZE * META_TO_CUE_GAP
        val metaTop = minOf(top + HALF_HEIGHT - META_BOTTOM_OFFSET, footerTop - blockHeight)
        drawLine(canvas, meta, p.muted, META_SIZE, false, metaTop, bounds)
        if (cue != null) drawLine(canvas, CUE_PREFIX + cue, p.accent, CUE_SIZE, true, metaTop + META_SIZE * META_TO_CUE_GAP, bounds)
    }

    private fun drawFooter(canvas: Canvas, bounds: MutableList<RectF>?) {
        val disclaimer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BOTTOM_MUTED
            textSize = DISCLAIMER_SIZE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(DISCLAIMER, 0, DISCLAIMER.length, disclaimer, CONTENT_WIDTH)
            .setMaxLines(DISCLAIMER_LINES)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(MARGIN.toFloat(), CARD_HEIGHT - DISCLAIMER_TOP_OFFSET)
        layout.draw(canvas)
        canvas.restore()
        bounds?.add(blockBounds(layout, CARD_HEIGHT - DISCLAIMER_TOP_OFFSET))

        val watermark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BOTTOM_ACCENT
            textSize = WATERMARK_SIZE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val baseline = CARD_HEIGHT - FOOTER_BASELINE_OFFSET
        canvas.drawText(WATERMARK, (CARD_WIDTH - MARGIN).toFloat(), baseline, watermark)
        val metrics = watermark.fontMetrics
        val width = watermark.measureText(WATERMARK)
        bounds?.add(RectF(CARD_WIDTH - MARGIN - width, baseline + metrics.ascent, (CARD_WIDTH - MARGIN).toFloat(), baseline + metrics.descent))
    }

    /** Rectangle the laid-out text really occupies (widest line, full height), in card coordinates. */
    private fun blockBounds(layout: StaticLayout, top: Float): RectF {
        val width = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
        return RectF(MARGIN.toFloat(), top, MARGIN + width, top + layout.height)
    }

    private fun drawLine(canvas: Canvas, text: String, color: Int, size: Float, bold: Boolean, top: Float, bounds: MutableList<RectF>?) {
        if (text.isBlank()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .build()
        canvas.save()
        canvas.translate(MARGIN.toFloat(), top)
        layout.draw(canvas)
        canvas.restore()
        bounds?.add(blockBounds(layout, top))
    }
}
