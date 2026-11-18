package com.mckimquyen.reader.infrastructure.social.nostr

import java.util.Locale

/** Bech32 (BIP-173) decode/encode, enough for NIP-19. Pure Kotlin, no Android. */
internal object Bech32 {
    class Decoded(val hrp: String, val payload: ByteArray)

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val SEPARATOR = '1'
    private const val CHECKSUM_LENGTH = 6
    /** BIP-173's limit, kept for npub and note1; a caller that reads a longer NIP-19 string (nprofile) says so. */
    const val DEFAULT_MAX_LENGTH = 90
    private const val MIN_PRINTABLE = 33
    private const val MAX_PRINTABLE = 126
    private const val BITS_PER_CHAR = 5
    private const val BITS_PER_BYTE = 8
    private const val CHECKSUM_SHIFT = 25
    private const val CHECKSUM_MASK = 0x1ffffff
    private const val CHAR_MASK = 31
    private const val CHECKSUM_CONSTANT = 1
    private val GENERATOR = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)

    fun decode(text: String, maxLength: Int = DEFAULT_MAX_LENGTH): Decoded? {
        if (text.length > maxLength) return null
        if (text.any { it.code < MIN_PRINTABLE || it.code > MAX_PRINTABLE }) return null
        if (text != text.lowercase(Locale.ROOT) && text != text.uppercase(Locale.ROOT)) return null
        val lower = text.lowercase(Locale.ROOT)
        val sep = lower.lastIndexOf(SEPARATOR)
        if (sep < 1 || sep + 1 + CHECKSUM_LENGTH > lower.length) return null
        val hrp = lower.substring(0, sep)
        val data = IntArray(lower.length - sep - 1)
        for (i in data.indices) {
            val v = CHARSET.indexOf(lower[sep + 1 + i])
            if (v < 0) return null
            data[i] = v
        }
        if (polymod(expandHrp(hrp) + data) != CHECKSUM_CONSTANT) return null
        val bytes = convertBits(data.copyOf(data.size - CHECKSUM_LENGTH), BITS_PER_CHAR, BITS_PER_BYTE, pad = false) ?: return null
        return Decoded(hrp, ByteArray(bytes.size) { bytes[it].toByte() })
    }

    fun encode(hrp: String, payload: ByteArray): String {
        val values = convertBits(IntArray(payload.size) { payload[it].toInt() and 0xff }, BITS_PER_BYTE, BITS_PER_CHAR, pad = true)
            ?: error("padding conversion cannot fail")
        val mod = polymod(expandHrp(hrp) + values + IntArray(CHECKSUM_LENGTH)) xor CHECKSUM_CONSTANT
        val checksum = IntArray(CHECKSUM_LENGTH) { (mod shr (BITS_PER_CHAR * (CHECKSUM_LENGTH - 1 - it))) and CHAR_MASK }
        return buildString {
            append(hrp).append(SEPARATOR)
            (values + checksum).forEach { append(CHARSET[it]) }
        }
    }

    private fun expandHrp(hrp: String): IntArray =
        IntArray(hrp.length * 2 + 1).also { out ->
            hrp.forEachIndexed { i, c ->
                out[i] = c.code shr BITS_PER_CHAR
                out[hrp.length + 1 + i] = c.code and CHAR_MASK
            }
        }

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val top = chk ushr CHECKSUM_SHIFT
            chk = ((chk and CHECKSUM_MASK) shl BITS_PER_CHAR) xor v
            for (i in GENERATOR.indices) if ((top shr i) and 1 == 1) chk = chk xor GENERATOR[i]
        }
        return chk
    }

    private fun convertBits(data: IntArray, from: Int, to: Int, pad: Boolean): IntArray? {
        var acc = 0
        var bits = 0
        val maxValue = (1 shl to) - 1
        val out = ArrayList<Int>()
        for (v in data) {
            acc = (acc shl from) or v
            bits += from
            while (bits >= to) {
                bits -= to
                out.add((acc shr bits) and maxValue)
            }
            acc = acc and ((1 shl bits) - 1)
        }
        if (pad) {
            if (bits > 0) out.add((acc shl (to - bits)) and maxValue)
        } else if (bits >= from || ((acc shl (to - bits)) and maxValue) != 0) {
            return null
        }
        return out.toIntArray()
    }
}
