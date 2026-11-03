package com.mckimquyen.reader.domain.p2psync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEnvelopeCryptoTest {
    private val secret = PairingSecret.fromHex("0123456789abcdef".repeat(2))
    private val plain = "private-state-α".toByteArray(Charsets.UTF_8)

    @Test fun encryptDecryptRoundTrip() {
        assertArrayEquals(plain, SyncEnvelopeCrypto.decrypt(SyncEnvelopeCrypto.encrypt(plain, secret), secret))
    }

    @Test fun saltAndNonceAreFreshEveryTime() {
        val first = SyncEnvelopeCrypto.encrypt(plain, secret)
        val second = SyncEnvelopeCrypto.encrypt(plain, secret)
        assertFalse(first.contentEquals(second))
        assertFalse(first.copyOfRange(SyncEnvelopeCrypto.SALT_OFFSET, SyncEnvelopeCrypto.NONCE_OFFSET)
            .contentEquals(second.copyOfRange(SyncEnvelopeCrypto.SALT_OFFSET, SyncEnvelopeCrypto.NONCE_OFFSET)))
        assertFalse(first.copyOfRange(SyncEnvelopeCrypto.NONCE_OFFSET, SyncEnvelopeCrypto.CIPHERTEXT_OFFSET)
            .contentEquals(second.copyOfRange(SyncEnvelopeCrypto.NONCE_OFFSET, SyncEnvelopeCrypto.CIPHERTEXT_OFFSET)))
    }

    @Test fun wrongSecretFailsAuthentication() {
        val envelope = SyncEnvelopeCrypto.encrypt(plain, secret)
        val wrong = PairingSecret.fromHex("fedcba9876543210".repeat(2))
        assertTrue(runCatching { SyncEnvelopeCrypto.decrypt(envelope, wrong) }.isFailure)
    }

    @Test fun changedMagicVersionSaltNonceCiphertextOrTagIsRejected() {
        val envelope = SyncEnvelopeCrypto.encrypt(plain, secret)
        listOf(0, 3, SyncEnvelopeCrypto.SALT_OFFSET, SyncEnvelopeCrypto.NONCE_OFFSET,
            SyncEnvelopeCrypto.CIPHERTEXT_OFFSET, envelope.lastIndex).forEach { index ->
            val tampered = envelope.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertTrue("byte $index", runCatching { SyncEnvelopeCrypto.decrypt(tampered, secret) }.isFailure)
        }
    }

    @Test fun malformedAndOversizeEnvelopeRejected() {
        val envelope = SyncEnvelopeCrypto.encrypt(plain, secret)
        listOf(byteArrayOf(), ByteArray(SyncEnvelopeCrypto.OVERHEAD_BYTES - 1),
            envelope.copyOf().also { it[0] = 0 },
            envelope.copyOf().also { it[SyncEnvelopeCrypto.VERSION_OFFSET] = 99.toByte() },
            ByteArray(SyncLimits.MAX_ENVELOPE_BYTES + 1)).forEach {
            assertTrue(runCatching { SyncEnvelopeCrypto.decrypt(it, secret) }.isFailure)
        }
    }

    @Test fun plaintextMarkerIsNotPresentInTheEnvelope() {
        val marker = "RssCatHubPrivateMarker-7f90".toByteArray()
        val envelope = SyncEnvelopeCrypto.encrypt(marker, secret)
        assertFalse(envelope.toString(Charsets.ISO_8859_1).contains(String(marker, Charsets.ISO_8859_1)))
    }

    @Test fun maxAllowedCiphertextRoundTripsAndOneByteOverIsRejected() {
        val max = ByteArray(SyncLimits.MAX_ENVELOPE_BYTES - SyncEnvelopeCrypto.OVERHEAD_BYTES) { (it % 251).toByte() }
        assertArrayEquals(max, SyncEnvelopeCrypto.decrypt(SyncEnvelopeCrypto.encrypt(max, secret), secret))
        assertTrue(runCatching {
            SyncEnvelopeCrypto.encrypt(ByteArray(SyncLimits.MAX_ENVELOPE_BYTES), secret)
        }.isFailure)
    }
}
