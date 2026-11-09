package com.mckimquyen.reader.infrastructure.p2psync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.SecretKey

/** Real Android Keystore. Never deletes the key: the app's own pairing entries may depend on the same alias. */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretWrapperDeviceTest {
    private val wrapper = KeystoreSecretWrapper()
    private val plain = ByteArray(16) { it.toByte() }

    @Test fun roundTripsThroughTheRealKeystore() {
        assertArrayEquals(plain, wrapper.unwrap(wrapper.wrap(plain)))
    }

    @Test fun twoWrapsOfTheSameSecretDiffer() {
        assertFalse(wrapper.wrap(plain).contentEquals(wrapper.wrap(plain)))
    }

    @Test fun wrappedBytesDoNotContainThePlainSecret() {
        val wrapped = wrapper.wrap(plain).toList()
        assertFalse(wrapped.windowed(plain.size).any { it == plain.toList() })
    }

    @Test fun aTamperedCiphertextIsRejectedWithNull() {
        val wrapped = wrapper.wrap(plain)
        wrapped[wrapped.lastIndex] = (wrapped.last().toInt() xor 1).toByte()
        assertNull(wrapper.unwrap(wrapped))
    }

    @Test fun truncatedOrEmptyInputIsRejectedWithNull() {
        assertNull(wrapper.unwrap(ByteArray(0)))
        assertNull(wrapper.unwrap(wrapper.wrap(plain).copyOf(5)))
    }

    @Test fun theKeyCannotBeExported() {
        wrapper.wrap(plain)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey("rsscathub_pairing_v1", null) as SecretKey
        assertNotNull(key)
        assertNull("Keystore key material must not be readable", key.encoded)
        assertEquals("AES", key.algorithm)
    }
}
