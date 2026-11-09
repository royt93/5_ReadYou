package com.mckimquyen.reader.domain.p2psync

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Derives the one-session token both paired devices can compute independently from the stored secret and the public
 * per-session nonce. Uses only the platform's JCA HMAC, no custom crypto. The token never goes into NSD; only the
 * nonce does, so a stranger on the LAN can see that a session exists but cannot compute the path that serves it.
 */
object SessionTokenDerivation {
    const val NONCE_BYTES = 16
    const val TOKEN_BYTES = 16
    private const val HMAC = "HmacSHA256"
    private val CONTEXT = "RCS-session-v1".toByteArray(Charsets.US_ASCII)
    private const val HEX = "0123456789abcdef"

    fun newNonce(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(NONCE_BYTES).also(random::nextBytes)

    fun nonceHex(nonce: ByteArray): String = toHex(nonce)

    fun nonceFromHex(hex: String): ByteArray? {
        if (hex.length != NONCE_BYTES * 2 || hex.any { it !in HEX }) return null
        return fromHex(hex)
    }

    fun derive(secret: PairingSecret, nonce: ByteArray): String {
        require(nonce.size == NONCE_BYTES)
        val key = fromHex(secret.hex)
        return try {
            val mac = Mac.getInstance(HMAC)
            mac.init(SecretKeySpec(key, HMAC))
            mac.update(CONTEXT)
            mac.update(nonce)
            toHex(mac.doFinal().copyOf(TOKEN_BYTES))
        } finally {
            key.fill(0)
        }
    }

    /** Constant-time, so response timing never reveals how many leading characters of a guess were right. */
    fun matches(expected: String, candidate: String?): Boolean {
        if (candidate == null || candidate.length != expected.length) return false
        return MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), candidate.toByteArray(Charsets.US_ASCII))
    }

    private fun fromHex(text: String): ByteArray =
        ByteArray(text.length / 2) { i -> ((HEX.indexOf(text[i * 2]) shl 4) or HEX.indexOf(text[i * 2 + 1])).toByte() }

    private fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach {
            val v = it.toInt() and 0xFF
            append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
    }
}
