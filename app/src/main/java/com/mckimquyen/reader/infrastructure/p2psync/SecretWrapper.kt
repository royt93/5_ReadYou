package com.mckimquyen.reader.infrastructure.p2psync

/** Device-bound encryption boundary; production uses Android Keystore, unit tests inject a deterministic wrapper. */
interface SecretWrapper {
    /** Returns a self-describing ciphertext, including any IV/nonce needed to unwrap it. */
    fun wrap(plain: ByteArray): ByteArray

    /** Returns null when the Keystore key is missing or the ciphertext is invalid. */
    fun unwrap(wrapped: ByteArray): ByteArray?
}

/**
 * Wraps pairing secrets with a non-exportable AES-256-GCM key kept in the Android Keystore. Format of the result:
 * `nonce (12 bytes) || ciphertext || tag`. No user authentication is required, so a paired sync can run unattended.
 *
 * UNVERIFIED ON A DEVICE: written against the platform API and compiled, not yet run against a real Keystore.
 */
@javax.inject.Singleton
class KeystoreSecretWrapper @javax.inject.Inject constructor() : SecretWrapper {
    private val alias = KEY_ALIAS

    override fun wrap(plain: ByteArray): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key(createIfMissing = true))
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun unwrap(wrapped: ByteArray): ByteArray? {
        if (wrapped.size < NONCE_BYTES + TAG_BYTES) return null
        return try {
            val key = key(createIfMissing = false) ?: return null
            val cipher = javax.crypto.Cipher.getInstance(TRANSFORMATION)
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(TAG_BITS, wrapped, 0, NONCE_BYTES))
            cipher.doFinal(wrapped, NONCE_BYTES, wrapped.size - NONCE_BYTES)
        } catch (_: java.security.GeneralSecurityException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
    }

    private fun key(createIfMissing: Boolean): javax.crypto.SecretKey? {
        val store = java.security.KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? javax.crypto.SecretKey)?.let { return it }
        if (!createIfMissing) return null
        val generator = javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rsscathub_pairing_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
    }
}
