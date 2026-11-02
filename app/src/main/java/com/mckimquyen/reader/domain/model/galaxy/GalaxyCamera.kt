package com.mckimquyen.reader.domain.model.galaxy

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Where a [GalaxyPoint] lands on the canvas. [depth] is smaller for nearer points; [scale] grows as they near. */
data class GalaxyProjection(val x: Float, val y: Float, val depth: Float, val scale: Float)

/**
 * Pure orbit camera. This is a perspective projection of 3D points onto the 2D canvas, not a GPU 3D engine:
 * rotate yaw (around the vertical axis) then pitch (tilt), then divide by distance.
 */
data class GalaxyCamera(
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val zoom: Float = 1f,
) {
    val isDefault: Boolean get() = this == DEFAULT

    fun rotateBy(dx: Float, dy: Float): GalaxyCamera {
        if (!dx.isFinite() || !dy.isFinite()) return this
        val newYaw = Math.IEEEremainder((yaw + dx * RADIANS_PER_PX).toDouble(), TWO_PI).toFloat()
        val newPitch = (pitch + dy * RADIANS_PER_PX).coerceIn(-MAX_PITCH, MAX_PITCH)
        return copy(yaw = newYaw, pitch = newPitch)
    }

    fun zoomBy(factor: Float): GalaxyCamera {
        if (!factor.isFinite() || factor <= 0f) return this
        return copy(zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM))
    }

    fun project(point: GalaxyPoint, width: Float, height: Float): GalaxyProjection? {
        if (!(width > 0f) || !(height > 0f) || !width.isFinite() || !height.isFinite()) return null
        if (!point.x.isFinite() || !point.y.isFinite() || !point.z.isFinite()) return null
        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)
        val x1 = point.x * cosYaw + point.z * sinYaw
        val z1 = -point.x * sinYaw + point.z * cosYaw
        val y2 = point.y * cosPitch - z1 * sinPitch
        val z2 = point.y * sinPitch + z1 * cosPitch
        val distance = CAMERA_DISTANCE + z2
        if (distance < MIN_DISTANCE) return null
        val scale = CAMERA_DISTANCE / distance
        val unit = min(width, height) / 2f * FILL_FRACTION * zoom * scale
        return GalaxyProjection(
            x = width / 2f + x1 * unit,
            y = height / 2f + y2 * unit,
            depth = z2,
            scale = scale,
        )
    }

    /** Farthest first, so nearer stars paint over farther ones; ties by id keep the order stable between frames. */
    fun depthOrder(points: Map<String, GalaxyPoint>): List<String> =
        points.entries
            .mapNotNull { (id, p) -> project(p, REFERENCE_SIZE, REFERENCE_SIZE)?.let { id to it.depth } }
            .sortedWith(compareByDescending<Pair<String, Float>> { it.second }.thenBy { it.first })
            .map { it.first }

    fun hitTest(
        stars: List<GalaxyStar>,
        positions: Map<String, GalaxyPoint>,
        width: Float,
        height: Float,
        x: Float,
        y: Float,
        radiusPx: Float,
    ): String? {
        val limit = radiusPx * radiusPx
        return stars.asSequence()
            .mapNotNull { star ->
                val projected = positions[star.id]?.let { project(it, width, height) } ?: return@mapNotNull null
                val dx = projected.x - x
                val dy = projected.y - y
                val distance = dx * dx + dy * dy
                if (distance <= limit) Triple(star.id, distance, projected.depth) else null
            }
            .minWithOrNull(compareBy<Triple<String, Float, Float>>({ it.second }, { it.third }, { it.first }))
            ?.first
    }

    companion object {
        const val MAX_PITCH = 1.3f
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 5f

        /** Tilted a little so the flat disc already reads as three-dimensional on first open. */
        val DEFAULT = GalaxyCamera(yaw = 0f, pitch = 0.6f, zoom = 1f)

        private const val RADIANS_PER_PX = 0.01f
        private const val TWO_PI = 2.0 * Math.PI
        private const val CAMERA_DISTANCE = 3f
        private const val MIN_DISTANCE = 0.2f
        private const val FILL_FRACTION = 0.8f
        private const val REFERENCE_SIZE = 1000f
    }
}
