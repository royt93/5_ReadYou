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

    @Test fun oversizeEnvelopeIsRejectedBeforeAnyKeyDerivation() {
        // A valid-looking header on an oversize body: only the size check can reject it quickly (PBKDF2 alone costs far more).
        val valid = SyncEnvelopeCrypto.encrypt(plain, secret)
        val header = valid.copyOf(SyncEnvelopeCrypto.CIPHERTEXT_OFFSET)
        val oversize = header + ByteArray(SyncLimits.MAX_ENVELOPE_BYTES + 1 - header.size)
        val kdfStartNanos = System.nanoTime()
        SyncEnvelopeCrypto.decrypt(valid, secret)
        val kdfMillis = maxOf(1L, (System.nanoTime() - kdfStartNanos) / 1_000_000)
        val startNanos = System.nanoTime()
        val failure = runCatching { SyncEnvelopeCrypto.decrypt(oversize, secret) }.exceptionOrNull()
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000
        assertTrue(failure is IllegalArgumentException)
        assertTrue("oversize rejected in ${elapsedMillis}ms; one KDF took ${kdfMillis}ms", elapsedMillis < kdfMillis)
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
