package com.mckimquyen.reader.infrastructure.social.nostr

import java.math.BigInteger
import java.security.MessageDigest

/** BIP-340 Schnorr verification only; no secret key is accepted or retained. */
object Bip340 {
    private val FIELD = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
    private val ORDER = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    private val GX = BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16)
    private val GY = BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)
    private val TWO = BigInteger.valueOf(2)
    private val THREE = BigInteger.valueOf(3)
    private val SEVEN = BigInteger.valueOf(7)
    private val EIGHT = BigInteger.valueOf(8)
    private val ZERO = BigInteger.ZERO
    private val ONE = BigInteger.ONE
    private const val FIELD_BYTES = 32
    private const val SIGNATURE_BYTES = 64
    private const val HEX_RADIX = 16
    private val INFINITY = Point(ZERO, ONE, ZERO)
    private val GENERATOR = Point(GX, GY, ONE)

    fun verify(pubkeyHex: String, messageHex: String, signatureHex: String): Boolean {
        return try {
            val pubkey = decodeHex(pubkeyHex) ?: return false
            val message = decodeHex(messageHex) ?: return false
            val signature = decodeHex(signatureHex) ?: return false
            if (pubkey.size != FIELD_BYTES || signature.size != SIGNATURE_BYTES) return false

            val publicPoint = liftX(BigInteger(1, pubkey)) ?: return false
            val r = BigInteger(1, signature.copyOfRange(0, FIELD_BYTES))
            val s = BigInteger(1, signature.copyOfRange(FIELD_BYTES, SIGNATURE_BYTES))
            // ponytail: BIP-340 requires s < n. No test can tell this guard from its absence: a valid signature with
            // s >= n needs s - n below 2^129, odds about 2^-127. Kept for spec conformance. r < p needs no guard of its own
            // because the final x comparison already fails for any r >= p.
            if (s >= ORDER) return false

            val challengeInput = signature.copyOfRange(0, FIELD_BYTES) + pubkey + message
            val challenge = BigInteger(1, taggedHash("BIP0340/challenge", challengeInput)).mod(ORDER)
            val result = toAffine(add(multiply(s, GENERATOR), multiply(ORDER.subtract(challenge).mod(ORDER), publicPoint)))
                ?: return false
            result.y.testBit(0).not() && result.x == r
        } catch (_: ArithmeticException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private data class Point(val x: BigInteger, val y: BigInteger, val z: BigInteger)
    private data class Affine(val x: BigInteger, val y: BigInteger)

    private fun decodeHex(value: String): ByteArray? {
        if (value.length % 2 != 0 || value.any { it.digitToIntOrNull(HEX_RADIX) == null }) return null
        return runCatching {
            ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(HEX_RADIX).toByte() }
        }.getOrNull()
    }

    private fun liftX(x: BigInteger): Point? {
        if (x >= FIELD) return null
        val ySquared = x.modPow(THREE, FIELD).add(SEVEN).mod(FIELD)
        var y = ySquared.modPow(FIELD.add(ONE).divide(BigInteger.valueOf(4)), FIELD)
        if (y.multiply(y).mod(FIELD) != ySquared) return null
        if (y.testBit(0)) y = FIELD.subtract(y)
        return Point(x, y, ONE)
    }

    private fun taggedHash(tag: String, message: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val tagHash = digest.digest(tag.toByteArray(Charsets.UTF_8))
        return MessageDigest.getInstance("SHA-256").digest(tagHash + tagHash + message)
    }

    private fun multiply(scalar: BigInteger, point: Point): Point {
        var n = scalar
        var addend = point
        var result = INFINITY
        while (n.signum() > 0) {
            if (n.testBit(0)) result = add(result, addend)
            addend = double(addend)
            n = n.shiftRight(1)
        }
        return result
    }

    private fun double(point: Point): Point {
        if (point.z == ZERO || point.y == ZERO) return INFINITY
        val a = point.x.multiply(point.x).mod(FIELD)
        val b = point.y.multiply(point.y).mod(FIELD)
        val c = b.multiply(b).mod(FIELD)
        val d = TWO.multiply(point.x.add(b).pow(2).subtract(a).subtract(c)).mod(FIELD)
        val e = THREE.multiply(a).mod(FIELD)
        val f = e.multiply(e).mod(FIELD)
        val x = f.subtract(TWO.multiply(d)).mod(FIELD)
        val y = e.multiply(d.subtract(x)).subtract(EIGHT.multiply(c)).mod(FIELD)
        val z = TWO.multiply(point.y).multiply(point.z).mod(FIELD)
        return Point(x, y, z)
    }

    private fun add(p: Point, q: Point): Point {
        if (p.z == ZERO) return q
        if (q.z == ZERO) return p
        val z1z1 = p.z.multiply(p.z).mod(FIELD)
        val z2z2 = q.z.multiply(q.z).mod(FIELD)
        val u1 = p.x.multiply(z2z2).mod(FIELD)
        val u2 = q.x.multiply(z1z1).mod(FIELD)
        val s1 = p.y.multiply(q.z).multiply(z2z2).mod(FIELD)
        val s2 = q.y.multiply(p.z).multiply(z1z1).mod(FIELD)
        val h = u2.subtract(u1).mod(FIELD)
        val r = TWO.multiply(s2.subtract(s1)).mod(FIELD)
        if (h == ZERO) return if (r == ZERO) double(p) else INFINITY
        val i = TWO.multiply(h).pow(2).mod(FIELD)
        val j = h.multiply(i).mod(FIELD)
        val v = u1.multiply(i).mod(FIELD)
        val x = r.multiply(r).subtract(j).subtract(TWO.multiply(v)).mod(FIELD)
        val y = r.multiply(v.subtract(x)).subtract(TWO.multiply(s1).multiply(j)).mod(FIELD)
        val z = p.z.add(q.z).pow(2).subtract(z1z1).subtract(z2z2).multiply(h).mod(FIELD)
        return Point(x, y, z)
    }

    private fun toAffine(point: Point): Affine? {
        if (point.z == ZERO) return null
        val inverse = point.z.modPow(FIELD.subtract(TWO), FIELD)
        val inverseSquared = inverse.multiply(inverse).mod(FIELD)
        val x = point.x.multiply(inverseSquared).mod(FIELD)
        val y = point.y.multiply(inverseSquared).multiply(inverse).mod(FIELD)
        return Affine(x, y)
    }
}
