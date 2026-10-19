package com.mckimquyen.reader.infrastructure.quote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import com.mckimquyen.reader.domain.model.quote.QuoteCardSpec
import com.mckimquyen.reader.domain.model.quote.QuoteCardTemplate
import com.mckimquyen.reader.domain.model.quote.QuoteTextTruncator

/** Vẽ thẻ trích dẫn 1080x1350 chỉ bằng android.graphics; thuần, không cần Context. */
object QuoteCardRenderer {
    const val CARD_WIDTH = 1080
    const val CARD_HEIGHT = 1350
    const val WATERMARK = "RSS Cat Hub"

    private const val MARGIN = 90
    private const val QUOTE_MARK_BASELINE = 230f
    private const val QUOTE_MARK_SIZE = 220f
    private const val QUOTE_TOP = 260f
    private const val QUOTE_TEXT_SIZE = 56f
    private const val LINE_SPACING_MULT = 1.25f
    private const val MAX_QUOTE_LINES = 14
    private const val FOOTER_DIVIDER_Y = 1170f
    private const val DIVIDER_THICKNESS = 3f
    private const val FOOTER_AUTHOR_Y = 1205f
    private const val FOOTER_FEED_Y = 1255f
    private const val WATERMARK_BASELINE = 1315f
    private const val FOOTER_TEXT_SIZE = 34f
    private const val WATERMARK_TEXT_SIZE = 30f
    private const val OPENING_QUOTE_MARK = "“"

    private const val MINIMALIST_BG = 0xFFFAF7F2.toInt()
    private const val MINIMALIST_TEXT = 0xFF1B1B1B.toInt()
    private const val MINIMALIST_ACCENT = 0xFFB4553C.toInt()
    private const val MINIMALIST_MUTED = 0xFF6B6B6B.toInt()

    private const val GRADIENT_START = 0xFF4F6BED.toInt()
    private const val GRADIENT_END = 0xFF9C5BE0.toInt()
    private const val GRADIENT_TEXT = 0xFFFFFFFF.toInt()
    private const val GRADIENT_ACCENT = 0xFFFFE08A.toInt()
    private const val GRADIENT_MUTED = 0xFFE6E0FF.toInt()

    private const val DARK_BG = 0xFF121212.toInt()
    private const val DARK_TEXT = 0xFFF2F2F2.toInt()
    private const val DARK_ACCENT = 0xFF7DD3FC.toInt()
    private const val DARK_MUTED = 0xFF9A9A9A.toInt()

    private data class Palette(val text: Int, val accent: Int, val muted: Int)

    fun render(spec: QuoteCardSpec, template: QuoteCardTemplate): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = drawBackground(canvas, template)
        drawQuoteMark(canvas, palette)
        drawQuote(canvas, spec, palette)
        drawFooter(canvas, spec, palette)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas, template: QuoteCardTemplate): Palette = when (template) {
        QuoteCardTemplate.MINIMALIST -> {
            canvas.drawColor(MINIMALIST_BG)
            Palette(MINIMALIST_TEXT, MINIMALIST_ACCENT, MINIMALIST_MUTED)
        }

        QuoteCardTemplate.GRADIENT_MATERIAL -> {
            val paint = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, CARD_HEIGHT.toFloat(), GRADIENT_START, GRADIENT_END, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), paint)
            Palette(GRADIENT_TEXT, GRADIENT_ACCENT, GRADIENT_MUTED)
        }

        QuoteCardTemplate.DARK_STUDIO -> {
            canvas.drawColor(DARK_BG)
            Palette(DARK_TEXT, DARK_ACCENT, DARK_MUTED)
        }
    }

    private fun drawQuoteMark(canvas: Canvas, palette: Palette) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            textSize = QUOTE_MARK_SIZE
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText(OPENING_QUOTE_MARK, MARGIN.toFloat(), QUOTE_MARK_BASELINE, paint)
    }

    private fun drawQuote(canvas: Canvas, spec: QuoteCardSpec, palette: Palette) {
        val text = QuoteTextTruncator.truncate(spec.quote).text
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.text
            textSize = QUOTE_TEXT_SIZE
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, CARD_WIDTH - 2 * MARGIN)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING_MULT)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setMaxLines(MAX_QUOTE_LINES)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(MARGIN.toFloat(), QUOTE_TOP)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawFooter(canvas: Canvas, spec: QuoteCardSpec, palette: Palette) {
        val divider = Paint().apply {
            color = palette.muted
            strokeWidth = DIVIDER_THICKNESS
        }
        canvas.drawLine(MARGIN.toFloat(), FOOTER_DIVIDER_Y, (CARD_WIDTH - MARGIN).toFloat(), FOOTER_DIVIDER_Y, divider)

        val width = CARD_WIDTH - 2 * MARGIN
        spec.author?.takeIf { it.isNotBlank() }?.let {
            drawSingleLine(canvas, it, palette.text, FOOTER_TEXT_SIZE, true, FOOTER_AUTHOR_Y, width)
        }
        if (spec.feedName.isNotBlank()) {
            drawSingleLine(canvas, spec.feedName, palette.muted, FOOTER_TEXT_SIZE, false, FOOTER_FEED_Y, width)
        }

        val watermark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent
            textSize = WATERMARK_TEXT_SIZE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(WATERMARK, (CARD_WIDTH - MARGIN).toFloat(), WATERMARK_BASELINE, watermark)
    }

    private fun drawSingleLine(canvas: Canvas, text: String, color: Int, size: Float, bold: Boolean, top: Float, width: Int) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .build()
        canvas.save()
        canvas.translate(MARGIN.toFloat(), top)
        layout.draw(canvas)
        canvas.restore()
    }
}
