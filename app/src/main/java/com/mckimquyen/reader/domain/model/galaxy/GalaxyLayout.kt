package com.mckimquyen.reader.domain.model.galaxy

import kotlin.math.cos
import kotlin.math.sin

/** Position in galaxy space; the disc spans x/y in ±[GalaxyLayout.RADIUS], z in ±[GalaxyLayout.GALAXY_THICKNESS]. */
data class GalaxyPoint(val x: Float, val y: Float, val z: Float)

/**
 * Deterministic placement. A star's position depends only on its own id and concepts (never on the other stars),
 * so the galaxy does not reshuffle when a new article is read. Stars sharing a primary concept gather around one
 * centre on the disc; stars with no concept scatter on their own.
 */
object GalaxyLayout {
    const val RADIUS = 1f
    const val GALAXY_THICKNESS = 0.12f

    private const val CENTRE_MIN_RADIUS = 0.25f
    private const val CENTRE_MAX_RADIUS = 0.85f
    private const val CLUSTER_SPREAD = 0.12f

    /** Upper bound on the distance between two stars of one cluster: the spread in x, y and z (z uses the thickness). */
    const val MAX_CLUSTER_DIAMETER = 0.6f
    private const val FNV_OFFSET = -0x7ee3623b // 2166136261 as Int
    private const val FNV_PRIME = 16777619
    private const val UNIT_BITS_MASK = 0xFFFFFF
    private const val UNIT_DIVISOR = 0xFFFFFF.toFloat()
    private const val SALT_ANGLE = "angle:"
    private const val SALT_RADIUS = "radius:"
    private const val SALT_DX = "dx:"
    private const val SALT_DY = "dy:"
    private const val SALT_DZ = "dz:"
    private const val TWO_PI = (2.0 * Math.PI).toFloat()

    fun compute(stars: List<GalaxyStar>): Map<String, GalaxyPoint> =
        stars.associate { star -> star.id to place(star) }

    private fun place(star: GalaxyStar): GalaxyPoint {
        val clusterKey = star.concepts.minOrNull() ?: star.id
        val angle = unit(SALT_ANGLE + clusterKey) * TWO_PI
        val radius = CENTRE_MIN_RADIUS + unit(SALT_RADIUS + clusterKey) * (CENTRE_MAX_RADIUS - CENTRE_MIN_RADIUS)
        val cx = radius * cos(angle)
        val cy = radius * sin(angle)
        return GalaxyPoint(
            x = (cx + centred(SALT_DX + star.id) * CLUSTER_SPREAD).coerceIn(-RADIUS, RADIUS),
            y = (cy + centred(SALT_DY + star.id) * CLUSTER_SPREAD).coerceIn(-RADIUS, RADIUS),
            z = (centred(SALT_DZ + star.id) * GALAXY_THICKNESS).coerceIn(-GALAXY_THICKNESS, GALAXY_THICKNESS),
        )
    }

    /** 0..1 from a stable FNV-1a hash (String.hashCode mixes short ids poorly). */
    private fun unit(text: String): Float {
        var h = FNV_OFFSET
        for (ch in text) h = (h xor ch.code) * FNV_PRIME
        h = h xor (h ushr 15)
        h *= FNV_PRIME
        return ((h ushr 4) and UNIT_BITS_MASK) / UNIT_DIVISOR
    }

    /** -1..1 */
    private fun centred(text: String): Float = unit(text) * 2f - 1f
}
