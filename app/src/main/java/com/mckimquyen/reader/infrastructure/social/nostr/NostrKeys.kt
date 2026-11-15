package com.mckimquyen.reader.infrastructure.social.nostr

import java.util.Locale

/** NIP-19 helpers: public keys (`npub`) and note ids (`note1`). Never handles a secret key beyond flagging it. */
object NostrKeys {
    private const val NPUB_HRP = "npub"
    private const val NOTE_HRP = "note"
    private const val SECRET_PREFIX = "nsec1"
    private const val URI_PREFIX = "nostr:"
    private const val KEY_BYTES = 32
    private const val HEX_LENGTH = KEY_BYTES * 2
    private const val HEX_RADIX = 16

    fun npubToHex(input: String): String? {
        val decoded = Bech32.decode(withoutUriPrefix(input)) ?: return null
        if (decoded.hrp != NPUB_HRP || decoded.payload.size != KEY_BYTES) return null
        return decoded.payload.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun hexToNpub(hex: String): String? = fromHex(hex)?.let { Bech32.encode(NPUB_HRP, it) }

    fun noteId(eventIdHex: String): String? = fromHex(eventIdHex)?.let { Bech32.encode(NOTE_HRP, it) }

    /** True when the text looks like a Nostr SECRET key, which must never be used, sent or logged. */
    fun isSecretKey(input: String): Boolean = withoutUriPrefix(input).lowercase(Locale.ROOT).startsWith(SECRET_PREFIX)

    /** The box shows `nostr:<npub>` after a failed search, so that form must be read the same as the bare key. */
    private fun withoutUriPrefix(input: String): String {
        val text = input.trim()
        return if (text.startsWith(URI_PREFIX, ignoreCase = true)) text.substring(URI_PREFIX.length) else text
    }

    private fun fromHex(hex: String): ByteArray? {
        if (hex.length != HEX_LENGTH) return null
        return ByteArray(KEY_BYTES) { i ->
            hex.substring(i * 2, i * 2 + 2).toIntOrNull(HEX_RADIX)?.toByte() ?: return null
        }
    }
}
