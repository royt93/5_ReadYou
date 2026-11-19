package com.mckimquyen.reader.domain.p2psync

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The sync envelope: magic (4) + version (1) + salt (16) + nonce (12) + AES-256-GCM ciphertext with a 128-bit tag. The magic and version are
 * authenticated (they are the AAD), so an envelope cannot be relabelled to take the other key derivation.
 *
 * RCS2 (written now) derives the key with HKDF-SHA256 (RFC 5869). That is enough because the pairing secret is 128 random bits made by
 * [PairingSecret.generate] on the sending phone (a person never picks it), so there is nothing weak to slow down. RCS1 (read only, written by older
 * app versions) derived it with PBKDF2 over the hex text of the secret, 310,000 rounds, about 1.5 seconds on a phone.
 */
object SyncEnvelopeCrypto {
    private val MAGIC_RCS1 = byteArrayOf('R'.code.toByte(), 'C'.code.toByte(), 'S'.code.toByte(), '1'.code.toByte())
    private val MAGIC_RCS2 = byteArrayOf('R'.code.toByte(), 'C'.code.toByte(), 'S'.code.toByte(), '2'.code.toByte())
    private const val VERSION_RCS1: Byte = 1
    private const val VERSION_RCS2: Byte = 2
    private const val RCS2_INFO = "RCS2 envelope key"
    private const val HMAC = "HmacSHA256"
    private const val HKDF_FIRST_BLOCK: Byte = 1
    const val MAGIC_OFFSET = 0
    const val VERSION_OFFSET = 4
    const val SALT_OFFSET = 5
    const val NONCE_OFFSET = SALT_OFFSET + SyncLimits.SALT_BYTES
    const val CIPHERTEXT_OFFSET = NONCE_OFFSET + SyncLimits.GCM_NONCE_BYTES
    const val OVERHEAD_BYTES = CIPHERTEXT_OFFSET + SyncLimits.GCM_TAG_BITS / 8
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(plain: ByteArray, secret: PairingSecret, random: SecureRandom = SecureRandom()): ByteArray {
        require(plain.size <= SyncLimits.MAX_ENVELOPE_BYTES - OVERHEAD_BYTES)
        val salt = ByteArray(SyncLimits.SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(SyncLimits.GCM_NONCE_BYTES).also(random::nextBytes)
        val label = MAGIC_RCS2 + byteArrayOf(VERSION_RCS2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKeyRcs2(secret, salt), GCMParameterSpec(SyncLimits.GCM_TAG_BITS, nonce))
        cipher.updateAAD(label)
        return label + salt + nonce + cipher.doFinal(plain)
    }

    fun decrypt(envelope: ByteArray, secret: PairingSecret): ByteArray {
        require(envelope.size in OVERHEAD_BYTES..SyncLimits.MAX_ENVELOPE_BYTES)
        val magic = envelope.copyOfRange(MAGIC_OFFSET, VERSION_OFFSET)
        val version = envelope[VERSION_OFFSET]
        val rcs2 = magic.contentEquals(MAGIC_RCS2) && version == VERSION_RCS2
        val rcs1 = magic.contentEquals(MAGIC_RCS1) && version == VERSION_RCS1
        require(rcs1 || rcs2)
        val salt = envelope.copyOfRange(SALT_OFFSET, NONCE_OFFSET)
        val nonce = envelope.copyOfRange(NONCE_OFFSET, CIPHERTEXT_OFFSET)
        val key = if (rcs2) deriveKeyRcs2(secret, salt) else deriveKeyRcs1(secret, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(SyncLimits.GCM_TAG_BITS, nonce))
        cipher.updateAAD(magic + byteArrayOf(version))
        return cipher.doFinal(envelope, CIPHERTEXT_OFFSET, envelope.size - CIPHERTEXT_OFFSET)
    }

    /** HKDF-SHA256 (RFC 5869) with the salt as the extract salt and one expand block: the key is the 32 bytes of HMAC(PRK, info || 0x01). */
    private fun deriveKeyRcs2(secret: PairingSecret, salt: ByteArray): SecretKeySpec {
        val secretBytes = ByteArray(secret.hex.length / 2) { secret.hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
        try {
            val prk = hmac(salt, secretBytes)
            try {
                val okm = hmac(prk, RCS2_INFO.toByteArray(Charsets.US_ASCII) + byteArrayOf(HKDF_FIRST_BLOCK))
                require(okm.size * 8 == SyncLimits.KEY_BITS)
                return SecretKeySpec(okm, "AES").also { okm.fill(0) }
            } finally {
                prk.fill(0)
            }
        } finally {
            secretBytes.fill(0)
        }
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(key, HMAC))
        return mac.doFinal(data)
    }

    /** The RCS1 derivation, unchanged: only for reading envelopes written by older app versions. */
    private fun deriveKeyRcs1(secret: PairingSecret, salt: ByteArray): SecretKeySpec {
        val password = secret.hex.toCharArray()
        val spec = PBEKeySpec(password, salt, SyncLimits.PBKDF2_ITERATIONS, SyncLimits.KEY_BITS)
        return try {
            val key = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            SecretKeySpec(key, "AES").also { key.fill(0) }
        } finally {
            spec.clearPassword()
            password.fill('\u0000')
        }
    }
}
