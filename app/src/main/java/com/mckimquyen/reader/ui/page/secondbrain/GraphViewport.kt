package com.mckimquyen.reader.ui.page.secondbrain

import androidx.compose.ui.geometry.Offset

/**
 * Zoom/pan state for the Second Brain canvas. Pure value type so coordinate maths is unit-testable.
 * Screen = unit * (size * scale) + offset; offset is clamped so the unit square always covers the canvas.
 */
data class GraphViewport(
    val scale: Float = MIN_SCALE,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
) {
    val isIdentity: Boolean get() = scale == MIN_SCALE && offsetX == 0f && offsetY == 0f

    fun withSize(width: Float, height: Float): GraphViewport =
        copy(width = width, height = height).clamped()

    /** Zooms by [factor] keeping the content under ([focusX], [focusY]) where it is on screen. */
    fun zoomBy(factor: Float, focusX: Float, focusY: Float): GraphViewport {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (newScale == scale) return this
        val ratio = newScale / scale
        return copy(
            scale = newScale,
            offsetX = focusX - (focusX - offsetX) * ratio,
            offsetY = focusY - (focusY - offsetY) * ratio,
        ).clamped()
    }

    fun panBy(dx: Float, dy: Float): GraphViewport =
        copy(offsetX = offsetX + dx, offsetY = offsetY + dy).clamped()

    fun toScreen(p: NodePos): Offset = Offset(p.x * width * scale + offsetX, p.y * height * scale + offsetY)

    fun toUnit(x: Float, y: Float): NodePos {
        val w = width * scale
        val h = height * scale
        return NodePos(
            x = if (w == 0f) 0f else (x - offsetX) / w,
            y = if (h == 0f) 0f else (y - offsetY) / h,
        )
    }

    fun reset(): GraphViewport = copy(scale = MIN_SCALE, offsetX = 0f, offsetY = 0f)

    private fun clamped(): GraphViewport {
        val minX = -(width * scale - width).coerceAtLeast(0f)
        val minY = -(height * scale - height).coerceAtLeast(0f)
        return copy(offsetX = offsetX.coerceIn(minX, 0f), offsetY = offsetY.coerceIn(minY, 0f))
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 6f
    }
}
