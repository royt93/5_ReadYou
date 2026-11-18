package com.mckimquyen.reader.infrastructure.social.nostr

import java.util.Locale

/**
 * NIP-19 helpers: public keys (`npub`, `nprofile`) and note ids (`note1`). Never handles a secret key beyond flagging it.
 * An `nprofile` may carry relay hints chosen by whoever shared it; they are read over and NEVER used, so a pasted text cannot
 * make the app talk to a server of its choosing.
 */
object NostrKeys {
    private const val NPUB_HRP = "npub"
    private const val NOTE_HRP = "note"
    private const val NPROFILE_HRP = "nprofile"
    private const val NPROFILE_MAX_LENGTH = 1000
    private const val TLV_TYPE_PUBKEY = 0
    private const val TLV_HEADER_BYTES = 2
    private const val BYTE_MASK = 0xff
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

    /**
     * The public key of an `nprofile` (NIP-19), as lower-case hex; null when the text is not a well-formed one. The payload is a list
     * of type-length-value entries: type 0 is the key (exactly 32 bytes, exactly once); relay hints and unknown types are skipped.
     * Every byte must belong to an entry, so a cut or padded payload is refused rather than half read.
     */
    fun nprofileToHex(input: String): String? {
        val decoded = Bech32.decode(withoutUriPrefix(input), NPROFILE_MAX_LENGTH) ?: return null
        if (decoded.hrp != NPROFILE_HRP) return null
        val bytes = decoded.payload
        var key: ByteArray? = null
        var at = 0
        while (at < bytes.size) {
            if (at + TLV_HEADER_BYTES > bytes.size) return null
            val type = bytes[at].toInt() and BYTE_MASK
            val length = bytes[at + 1].toInt() and BYTE_MASK
            val start = at + TLV_HEADER_BYTES
            if (start + length > bytes.size) return null
            if (type == TLV_TYPE_PUBKEY) {
                if (length != KEY_BYTES || key != null) return null
                key = bytes.copyOfRange(start, start + length)
            }
            at = start + length
        }
        return key?.joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }
    }

    /** The one door the Add Source box uses: a public key typed as an `npub` or as an `nprofile`. */
    fun authorHexFrom(input: String): String? = npubToHex(input) ?: nprofileToHex(input)

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
