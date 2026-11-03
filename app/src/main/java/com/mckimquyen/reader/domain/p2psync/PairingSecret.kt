package com.mckimquyen.reader.domain.p2psync

import java.security.SecureRandom

@JvmInline
value class PairingSecret private constructor(val hex: String) {
    companion object {
        private const val HEX = "0123456789abcdef"

        fun generate(random: SecureRandom = SecureRandom()): PairingSecret {
            val bytes = ByteArray(SyncLimits.SECRET_BYTES).also(random::nextBytes)
            return PairingSecret(bytes.toLowerHex())
        }

        fun fromHex(hex: String): PairingSecret {
            require(hex.length == SyncLimits.SECRET_BYTES * 2 && hex.all { it in HEX })
            return PairingSecret(hex)
        }

        private fun ByteArray.toLowerHex(): String {
            val out = StringBuilder(size * 2)
            for (byte in this) {
                val value = byte.toInt() and 0xff
                out.append(HEX[value ushr 4]).append(HEX[value and 0x0f])
            }
            return out.toString()
        }
    }
}
