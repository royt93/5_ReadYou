package com.mckimquyen.reader.domain.p2psync

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SyncEnvelopeCrypto {
    private val MAGIC = byteArrayOf('R'.code.toByte(), 'C'.code.toByte(), 'S'.code.toByte(), '1'.code.toByte())
    private const val VERSION: Byte = 1
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
        val header = MAGIC + byteArrayOf(VERSION) + salt + nonce
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(secret, salt), GCMParameterSpec(SyncLimits.GCM_TAG_BITS, nonce))
        cipher.updateAAD(MAGIC + byteArrayOf(VERSION))
        return header + cipher.doFinal(plain)
    }

    fun decrypt(envelope: ByteArray, secret: PairingSecret): ByteArray {
        require(envelope.size in OVERHEAD_BYTES..SyncLimits.MAX_ENVELOPE_BYTES)
        require(envelope.copyOfRange(MAGIC_OFFSET, VERSION_OFFSET).contentEquals(MAGIC))
        require(envelope[VERSION_OFFSET] == VERSION)
        val salt = envelope.copyOfRange(SALT_OFFSET, NONCE_OFFSET)
        val nonce = envelope.copyOfRange(NONCE_OFFSET, CIPHERTEXT_OFFSET)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(secret, salt), GCMParameterSpec(SyncLimits.GCM_TAG_BITS, nonce))
        cipher.updateAAD(MAGIC + byteArrayOf(VERSION))
        return cipher.doFinal(envelope, CIPHERTEXT_OFFSET, envelope.size - CIPHERTEXT_OFFSET)
    }

    private fun deriveKey(secret: PairingSecret, salt: ByteArray): SecretKeySpec {
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
