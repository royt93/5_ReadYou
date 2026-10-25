package com.mckimquyen.reader.infrastructure.rpg

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

/** Renders a localized Brain Wrapped Story card without UI or Context dependencies. */
data class BrainWrappedRenderModel(
    val weekLabel: String,
    val header: String,
    val weeklyXpLabel: String,
    val weeklyXp: Long,
    val streakLabel: String,
    val streakValue: String,
    val topicLabel: String,
    val topicValue: String?,
    val topicEmptyLabel: String,
    val tagline: String,
    val watermark: String,
)

object BrainWrappedRenderer {
    const val CARD_WIDTH = 1080
    const val CARD_HEIGHT = 1920
    const val NAVY_START = 0xFF101126.toInt()
    const val HERO_SAMPLE_Y = 560

    private const val SAFE_MARGIN = 92
    private const val CONTENT_WIDTH = CARD_WIDTH - SAFE_MARGIN * 2
    private const val WEEK_LABEL_TOP = 112f
    private const val HEADER_TOP = 206f
    private const val XP_LABEL_TOP = 470f
    private const val XP_VALUE_TOP = 526f
    private const val METRIC_CARDS_TOP = 730f
    private const val METRIC_CARD_HEIGHT = 220f
    private const val METRIC_GAP = 24f
    private const val FOOTER_TOP = 1700f
    private const val WATERMARK_TOP = 1788f
    private const val CARD_RADIUS = 34f
    private const val LABEL_TEXT_SIZE = 30f
    private const val WEEK_TEXT_SIZE = 32f
    private const val HEADER_TEXT_SIZE = 76f
    private const val XP_TEXT_SIZE = 176f
    private const val METRIC_VALUE_TEXT_SIZE = 40f
    private const val METRIC_LABEL_TEXT_SIZE = 26f
    private const val FOOTER_TEXT_SIZE = 30f
    private const val WATERMARK_TEXT_SIZE = 34f
    private const val LINE_SPACING_MULTIPLIER = 1.08f
    private const val MAX_HEADER_LINES = 3
    private const val NAVY_END = 0xFF321653.toInt()
    private const val PANEL_COLOR = 0x24FFFFFF
    private const val PANEL_STROKE = 0x55D5B4FF
    private const val WHITE = 0xFFF8F7FF.toInt()
    private const val LAVENDER = 0xFFE7B8FF.toInt()
    private const val MUTED = 0xFFD4C6FF.toInt()

    fun render(model: BrainWrappedRenderModel): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas)
        drawWeekLabel(canvas, model.weekLabel)
        drawHeader(canvas, model.header)
        drawWeeklyXp(canvas, model.weeklyXpLabel, model.weeklyXp)
        drawMetricCards(canvas, model)
        drawFooter(canvas, model.tagline, model.watermark)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                CARD_WIDTH.toFloat(),
                CARD_HEIGHT.toFloat(),
                NAVY_START,
                NAVY_END,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), paint)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x448B5CF6
            maskFilter = android.graphics.BlurMaskFilter(150f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(CARD_WIDTH * 0.78f, 610f, 280f, glow)
    }

    private fun drawWeekLabel(canvas: Canvas, label: String) {
        drawSingleLine(
            canvas = canvas,
            text = label,
            top = WEEK_LABEL_TOP,
            width = CONTENT_WIDTH,
            size = WEEK_TEXT_SIZE,
            color = MUTED,
            bold = true,
            letterSpacing = 0.08f,
        )
    }

    private fun drawHeader(canvas: Canvas, header: String) {
        drawTextBlock(
            canvas = canvas,
            text = header,
            top = HEADER_TOP,
            width = CONTENT_WIDTH,
            size = HEADER_TEXT_SIZE,
            color = WHITE,
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
            maxLines = MAX_HEADER_LINES,
        )
    }

    private fun drawWeeklyXp(canvas: Canvas, label: String, xp: Long) {
        drawSingleLine(
            canvas = canvas,
            text = label,
            top = XP_LABEL_TOP,
            width = CONTENT_WIDTH,
            size = LABEL_TEXT_SIZE,
            color = MUTED,
            bold = true,
            letterSpacing = 0.06f,
        )
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LAVENDER
            textSize = XP_TEXT_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(34f, 0f, 0f, 0xCCB46BFF.toInt())
        }
        canvas.drawText("+$xp", SAFE_MARGIN.toFloat(), XP_VALUE_TOP + XP_TEXT_SIZE, paint)
    }

    private fun drawMetricCards(canvas: Canvas, model: BrainWrappedRenderModel) {
        val gap = METRIC_GAP
        val cardWidth = (CONTENT_WIDTH - gap) / 2f
        drawMetricCard(
            canvas = canvas,
            left = SAFE_MARGIN.toFloat(),
            title = model.streakLabel,
            value = model.streakValue,
            width = cardWidth,
        )
        drawMetricCard(
            canvas = canvas,
            left = SAFE_MARGIN + cardWidth + gap,
            title = model.topicLabel,
            value = model.topicValue?.takeIf(String::isNotBlank) ?: model.topicEmptyLabel,
            width = cardWidth,
        )
    }

    private fun drawMetricCard(canvas: Canvas, left: Float, title: String, value: String, width: Float) {
        val rect = RectF(left, METRIC_CARDS_TOP, left + width, METRIC_CARDS_TOP + METRIC_CARD_HEIGHT)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PANEL_COLOR
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, CARD_RADIUS, CARD_RADIUS, cardPaint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PANEL_STROKE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(rect, CARD_RADIUS, CARD_RADIUS, borderPaint)
        drawSingleLine(canvas, title, rect.left + 30f, rect.top + 30f, width - 60f, METRIC_LABEL_TEXT_SIZE, MUTED, true)
        drawTextBlock(
            canvas = canvas,
            text = value,
            left = rect.left + 30f,
            top = rect.top + 90f,
            width = (width - 60f).toInt(),
            size = METRIC_VALUE_TEXT_SIZE,
            color = WHITE,
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
            maxLines = 2,
        )
    }

    private fun drawFooter(canvas: Canvas, tagline: String, watermark: String) {
        drawTextBlock(
            canvas = canvas,
            text = tagline,
            top = FOOTER_TOP,
            width = CONTENT_WIDTH,
            size = FOOTER_TEXT_SIZE,
            color = MUTED,
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL),
            maxLines = 2,
        )
        drawSingleLine(
            canvas = canvas,
            text = watermark,
            top = WATERMARK_TOP,
            width = CONTENT_WIDTH,
            size = WATERMARK_TEXT_SIZE,
            color = LAVENDER,
            bold = true,
            letterSpacing = 0.08f,
        )
    }

    private fun drawSingleLine(
        canvas: Canvas,
        text: String,
        top: Float,
        width: Int,
        size: Float,
        color: Int,
        bold: Boolean,
        letterSpacing: Float = 0f,
    ) = drawSingleLine(canvas, text, SAFE_MARGIN.toFloat(), top, width.toFloat(), size, color, bold, letterSpacing)

    private fun drawSingleLine(
        canvas: Canvas,
        text: String,
        left: Float,
        top: Float,
        width: Float,
        size: Float,
        color: Int,
        bold: Boolean,
        letterSpacing: Float = 0f,
    ) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            this.letterSpacing = letterSpacing
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setMaxLines(1)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawTextBlock(
        canvas: Canvas,
        text: String,
        top: Float,
        width: Int,
        size: Float,
        color: Int,
        typeface: Typeface,
        maxLines: Int,
    ) = drawTextBlock(canvas, text, SAFE_MARGIN.toFloat(), top, width, size, color, typeface, maxLines)

    private fun drawTextBlock(
        canvas: Canvas,
        text: String,
        left: Float,
        top: Float,
        width: Int,
        size: Float,
        color: Int,
        typeface: Typeface,
        maxLines: Int,
    ) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            this.typeface = typeface
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }
}
