package com.mckimquyen.reader.domain.mirror

import java.security.MessageDigest
import java.security.SecureRandom

/** One session's secret: 128 random bits as lowercase hex, valid until [expiresAtMillis]. */
class MirrorToken(val value: String, val expiresAtMillis: Long) {

    /** Constant-time comparison so response timing never reveals how many leading characters were right. */
    fun matches(candidate: String?, nowMillis: Long): Boolean {
        if (candidate == null || nowMillis >= expiresAtMillis || candidate.length != value.length) return false
        return MessageDigest.isEqual(
            value.toByteArray(Charsets.US_ASCII),
            candidate.toByteArray(Charsets.US_ASCII),
        )
    }

    companion object {
        private const val HEX = "0123456789abcdef"

        fun generate(nowMillis: Long, random: SecureRandom = SecureRandom()): MirrorToken {
            val bytes = ByteArray(MirrorLimits.TOKEN_BYTES).also(random::nextBytes)
            val hex = buildString(bytes.size * 2) {
                bytes.forEach {
                    val v = it.toInt() and 0xFF
                    append(HEX[v ushr 4]).append(HEX[v and 0x0F])
                }
            }
            return MirrorToken(hex, nowMillis + MirrorLimits.TOKEN_TTL_MILLIS)
        }
    }
}
