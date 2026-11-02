package com.mckimquyen.reader.domain.model.galaxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalaxyCameraTest {
    private companion object {
        const val W = 1000f
        const val H = 800f
        const val EPS = 0.01f
    }

    private fun star(id: String) = GalaxyStar(id, "T $id", "F", 0L, emptySet())

    @Test
    fun project_originLandsOnTheCanvasCentre() {
        val p = GalaxyCamera().project(GalaxyPoint(0f, 0f, 0f), W, H)
        assertNotNull(p)
        assertEquals(W / 2, p!!.x, EPS)
        assertEquals(H / 2, p.y, EPS)
    }

    @Test
    fun project_rotatingChangesWhereAPointLands() {
        val point = GalaxyPoint(0.5f, 0.2f, 0f)
        val before = GalaxyCamera().project(point, W, H)!!
        val yawed = GalaxyCamera().rotateBy(dx = 100f, dy = 0f).project(point, W, H)!!
        val pitched = GalaxyCamera().rotateBy(dx = 0f, dy = 100f).project(point, W, H)!!
        assertNotEquals(before.x, yawed.x, EPS)
        assertNotEquals(before.y, pitched.y, EPS)
    }

    @Test
    fun project_nearerPointIsDrawnLargerThanFartherPoint() {
        val camera = GalaxyCamera()
        val near = camera.project(GalaxyPoint(0.3f, 0f, -0.1f), W, H)!!
        val far = camera.project(GalaxyPoint(0.3f, 0f, 0.1f), W, H)!!
        assertTrue(near.depth < far.depth)
        assertTrue(near.scale > far.scale)
    }

    @Test
    fun project_zoomPushesPointsAwayFromTheCentre() {
        val point = GalaxyPoint(0.5f, 0f, 0f)
        val base = GalaxyCamera().project(point, W, H)!!
        val zoomed = GalaxyCamera().zoomBy(2f).project(point, W, H)!!
        assertTrue(zoomed.x - W / 2 > base.x - W / 2)
    }

    @Test
    fun project_rejectsZeroSizeAndNonFiniteInput() {
        val camera = GalaxyCamera()
        assertNull(camera.project(GalaxyPoint(0f, 0f, 0f), 0f, H))
        assertNull(camera.project(GalaxyPoint(0f, 0f, 0f), W, -1f))
        assertNull(camera.project(GalaxyPoint(Float.NaN, 0f, 0f), W, H))
        assertNull(camera.project(GalaxyPoint(0f, Float.POSITIVE_INFINITY, 0f), W, H))
    }

    @Test
    fun rotateBy_clampsPitchAndKeepsYawFinite() {
        val up = GalaxyCamera().rotateBy(0f, 1_000_000f)
        val down = GalaxyCamera().rotateBy(0f, -1_000_000f)
        assertEquals(GalaxyCamera.MAX_PITCH, up.pitch, 0f)
        assertEquals(-GalaxyCamera.MAX_PITCH, down.pitch, 0f)
        assertTrue(GalaxyCamera().rotateBy(1_000_000f, 0f).yaw.isFinite())
    }

    @Test
    fun rotateBy_ignoresNonFiniteDeltas() {
        val camera = GalaxyCamera(yaw = 0.3f, pitch = 0.2f)
        assertEquals(camera, camera.rotateBy(Float.NaN, Float.POSITIVE_INFINITY))
    }

    @Test
    fun zoomBy_clampsAndIgnoresBadFactors() {
        assertEquals(GalaxyCamera.MAX_ZOOM, GalaxyCamera().zoomBy(1_000f).zoom, 0f)
        assertEquals(GalaxyCamera.MIN_ZOOM, GalaxyCamera().zoomBy(0.0001f).zoom, 0f)
        val camera = GalaxyCamera()
        assertEquals(camera, camera.zoomBy(0f))
        assertEquals(camera, camera.zoomBy(-2f))
        assertEquals(camera, camera.zoomBy(Float.NaN))
    }

    @Test
    fun default_isTiltedSoTheDiscReadsAsThreeDimensional() {
        assertTrue(GalaxyCamera.DEFAULT.pitch != 0f)
        assertEquals(1f, GalaxyCamera.DEFAULT.zoom, 0f)
        assertTrue(GalaxyCamera.DEFAULT.isDefault)
        assertTrue(!GalaxyCamera.DEFAULT.rotateBy(10f, 0f).isDefault)
    }

    @Test
    fun depthOrder_listsFarthestFirstAndBreaksTiesById() {
        val points = mapOf(
            "near" to GalaxyPoint(0f, 0f, -0.1f),
            "far" to GalaxyPoint(0f, 0f, 0.1f),
            "mid-b" to GalaxyPoint(0.1f, 0f, 0f),
            "mid-a" to GalaxyPoint(-0.1f, 0f, 0f),
        )
        assertEquals(listOf("far", "mid-a", "mid-b", "near"), GalaxyCamera().depthOrder(points))
    }

    @Test
    fun hitTest_returnsTheNearestStarInsideTheRadiusOrNull() {
        val camera = GalaxyCamera()
        val positions = mapOf("a" to GalaxyPoint(0f, 0f, 0f), "b" to GalaxyPoint(0.5f, 0f, 0f))
        val stars = listOf(star("a"), star("b"))
        val b = camera.project(positions.getValue("b"), W, H)!!

        assertEquals("b", camera.hitTest(stars, positions, W, H, b.x + 3f, b.y, radiusPx = 20f))
        assertEquals("a", camera.hitTest(stars, positions, W, H, W / 2 + 2f, H / 2, radiusPx = 20f))
        assertNull(camera.hitTest(stars, positions, W, H, 5f, 5f, radiusPx = 20f))
    }

    @Test
    fun hitTest_ignoresStarsWithoutAPositionAndBreaksTiesById() {
        val camera = GalaxyCamera()
        val positions = mapOf("b" to GalaxyPoint(0f, 0f, 0f), "a" to GalaxyPoint(0f, 0f, 0f))
        assertEquals("a", camera.hitTest(listOf(star("b"), star("a"), star("ghost")), positions, W, H, W / 2, H / 2, 10f))
        assertNull(camera.hitTest(listOf(star("ghost")), positions, W, H, W / 2, H / 2, 10f))
    }
}
