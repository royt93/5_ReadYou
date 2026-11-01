package com.mckimquyen.reader.ui.page.secondbrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphViewportTest {
    private val base = GraphViewport().withSize(1000f, 800f)

    @Test
    fun identity_atStart_andResetReturnsIt() {
        assertTrue(base.isIdentity)
        assertTrue(base.zoomBy(3f, 500f, 400f).reset().isIdentity)
    }

    @Test
    fun zoomBy_clampsScaleToRange() {
        assertEquals(GraphViewport.MAX_SCALE, base.zoomBy(100f, 500f, 400f).scale, 0.0001f)
        assertEquals(GraphViewport.MIN_SCALE, base.zoomBy(0.01f, 500f, 400f).scale, 0.0001f)
    }

    @Test
    fun zoomBy_keepsContentUnderTheFocusPointFixed() {
        val focusX = 300f
        val focusY = 200f
        val before = base.toUnit(focusX, focusY)
        val zoomed = base.zoomBy(2.5f, focusX, focusY)
        val after = zoomed.toUnit(focusX, focusY)
        assertEquals(before.x, after.x, 0.0001f)
        assertEquals(before.y, after.y, 0.0001f)
    }

    @Test
    fun panBy_isIgnoredAtScaleOne() {
        val panned = base.panBy(120f, -80f)
        assertEquals(0f, panned.offsetX, 0.0001f)
        assertEquals(0f, panned.offsetY, 0.0001f)
    }

    @Test
    fun panBy_isClampedSoTheUnitSquareCannotLeaveTheCanvas() {
        val zoomed = base.zoomBy(2f, 500f, 400f)
        val farRight = zoomed.panBy(100_000f, 100_000f)
        val farLeft = zoomed.panBy(-100_000f, -100_000f)
        // At scale 2 the content is 2000x1600: offset may range from -(content - canvas) to 0.
        assertEquals(0f, farRight.offsetX, 0.0001f)
        assertEquals(0f, farRight.offsetY, 0.0001f)
        assertEquals(-1000f, farLeft.offsetX, 0.0001f)
        assertEquals(-800f, farLeft.offsetY, 0.0001f)
    }

    @Test
    fun toScreen_andToUnit_areInverses() {
        val v = base.zoomBy(3f, 700f, 100f).panBy(-150f, 40f)
        val p = NodePos(0.31f, 0.77f)
        val screen = v.toScreen(p)
        val back = v.toUnit(screen.x, screen.y)
        assertEquals(p.x, back.x, 0.0001f)
        assertEquals(p.y, back.y, 0.0001f)
    }

    @Test
    fun zoomOut_toMinScale_recentresOffsetToZero() {
        val zoomed = base.zoomBy(4f, 100f, 100f).panBy(-500f, -500f)
        val out = zoomed.zoomBy(0.001f, 500f, 400f)
        assertEquals(1f, out.scale, 0.0001f)
        assertEquals(0f, out.offsetX, 0.0001f)
        assertEquals(0f, out.offsetY, 0.0001f)
    }

    @Test
    fun withSize_reclampsExistingOffset() {
        val zoomed = base.zoomBy(2f, 500f, 400f).panBy(-1000f, -800f)
        val smaller = zoomed.withSize(500f, 400f)
        assertTrue(smaller.offsetX >= -500f)
        assertTrue(smaller.offsetY >= -400f)
    }

    @Test
    fun zeroSizedViewport_neverProducesNaN() {
        val v = GraphViewport().zoomBy(2f, 0f, 0f).panBy(10f, 10f)
        assertFalse(v.offsetX.isNaN() || v.offsetY.isNaN() || v.scale.isNaN())
        val p = v.toUnit(5f, 5f)
        assertFalse(p.x.isNaN() || p.y.isNaN())
    }
}
