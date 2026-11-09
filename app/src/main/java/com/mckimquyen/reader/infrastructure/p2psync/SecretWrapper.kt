package com.mckimquyen.reader.infrastructure.p2psync

/** Device-bound encryption boundary; production uses Android Keystore, unit tests inject a deterministic wrapper. */
interface SecretWrapper {
    /** Returns a self-describing ciphertext, including any IV/nonce needed to unwrap it. */
    fun wrap(plain: ByteArray): ByteArray

    /** Returns null when the Keystore key is missing or the ciphertext is invalid. */
    fun unwrap(wrapped: ByteArray): ByteArray?
}
