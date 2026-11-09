package com.mckimquyen.reader.domain.p2psync

/**
 * The only thing a pairing session shows on the LAN: a protocol version and a per-session nonce. No device name,
 * no user name, no token, no secret. A paired peer derives the token itself from the nonce and its stored secret.
 */
object NsdTxtCodec {
    const val SERVICE_TYPE = "_rsscathub._tcp"
    const val KEY_VERSION = "v"
    const val KEY_NONCE = "n"
    const val VERSION = "1"

    fun encode(nonce: ByteArray): Map<String, ByteArray> = mapOf(
        KEY_VERSION to VERSION.toByteArray(Charsets.US_ASCII),
        KEY_NONCE to SessionTokenDerivation.nonceHex(nonce).toByteArray(Charsets.US_ASCII),
    )

    /** Returns the nonce, or null when the record is missing a key, carries any other key, or has a bad value. */
    fun decode(attributes: Map<String, ByteArray?>): ByteArray? {
        if (attributes.keys != setOf(KEY_VERSION, KEY_NONCE)) return null
        val version = attributes[KEY_VERSION]?.toString(Charsets.US_ASCII)
        if (version != VERSION) return null
        val nonceText = attributes[KEY_NONCE]?.toString(Charsets.US_ASCII) ?: return null
        return SessionTokenDerivation.nonceFromHex(nonceText)
    }
}
