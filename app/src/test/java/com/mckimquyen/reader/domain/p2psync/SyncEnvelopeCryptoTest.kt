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
        // The old RCS1 key derivation (PBKDF2) is still the slow one, so an RCS1 envelope is the right yardstick.
        val valid = frozenOldEnvelope(secret, plain)
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

    // ---- EXC-05f: RCS2 (HKDF) next to the old RCS1 (PBKDF2). The vectors below were made OUTSIDE this code, by plain JCA calls in a separate
    // program (PBKDF2 for RCS1; RFC 5869 HKDF-SHA256 for RCS2, whose key was also computed again with Python's hmac and hashlib).

    /** An RCS1 envelope built the way the old code did (PBKDF2 over the hex secret, AAD = "RCS1" + 1), kept here only to read old envelopes in tests. */
    private fun frozenOldEnvelope(secret: PairingSecret, plain: ByteArray): ByteArray {
        val salt = ByteArray(SyncLimits.SALT_BYTES) { it.toByte() }
        val nonce = ByteArray(SyncLimits.GCM_NONCE_BYTES) { (0xa0 + it).toByte() }
        val header = "RCS1".toByteArray(Charsets.US_ASCII) + byteArrayOf(1)
        val spec = javax.crypto.spec.PBEKeySpec(secret.hex.toCharArray(), salt, SyncLimits.PBKDF2_ITERATIONS, SyncLimits.KEY_BITS)
        val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(SyncLimits.GCM_TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + salt + nonce + cipher.doFinal(plain)
    }

    private val fixedSecret = PairingSecret.fromHex("0123456789abcdef0123456789abcdef")
    private val fixedPlain = "vector-plain-\u03b1".toByteArray(Charsets.UTF_8)
    private fun unhex(h: String) = ByteArray(h.length / 2) { h.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
    private val frozenRcs1 = unhex("5243533101000102030405060708090a0b0c0d0e0fa0a1a2a3a4a5a6a7a8a9aaab57b3cac4793c96ec7242df157c17d545cdcc729b00e664a638e7c710b23b67")
    private val frozenRcs2 = unhex("5243533202000102030405060708090a0b0c0d0e0fa0a1a2a3a4a5a6a7a8a9aaab35c775ed7333ec870e783812d0418d6855ef8cc9427e123850cb2e59a74179")

    @Test fun anOldRcs1EnvelopeStillOpens() {
        assertArrayEquals(fixedPlain, SyncEnvelopeCrypto.decrypt(frozenRcs1, fixedSecret))
    }

    @Test fun aRcs2EnvelopeMadeByAnIndependentProgramOpens() {
        assertArrayEquals(fixedPlain, SyncEnvelopeCrypto.decrypt(frozenRcs2, fixedSecret))
    }

    @Test fun encryptWritesRcs2() {
        val envelope = SyncEnvelopeCrypto.encrypt(plain, secret)
        assertArrayEquals("RCS2".toByteArray(Charsets.US_ASCII), envelope.copyOfRange(SyncEnvelopeCrypto.MAGIC_OFFSET, SyncEnvelopeCrypto.VERSION_OFFSET))
        assertTrue(envelope[SyncEnvelopeCrypto.VERSION_OFFSET] == 2.toByte())
    }

    @Test fun aRcs2EnvelopeRelabelledAsRcs1IsRejected() {
        // The label is part of the authenticated header, and RCS1 would derive a different key: it must not open as RCS1.
        val relabelled = frozenRcs2.copyOf().also { "RCS1".toByteArray(Charsets.US_ASCII).copyInto(it); it[SyncEnvelopeCrypto.VERSION_OFFSET] = 1 }
        assertTrue(runCatching { SyncEnvelopeCrypto.decrypt(relabelled, fixedSecret) }.isFailure)
    }

    @Test fun anRcs1EnvelopeRelabelledAsRcs2IsRejected() {
        val relabelled = frozenRcs1.copyOf().also { "RCS2".toByteArray(Charsets.US_ASCII).copyInto(it); it[SyncEnvelopeCrypto.VERSION_OFFSET] = 2 }
        assertTrue(runCatching { SyncEnvelopeCrypto.decrypt(relabelled, fixedSecret) }.isFailure)
    }

    @Test fun anUnknownLabelIsRejected() {
        val unknown = frozenRcs2.copyOf().also { "RCS3".toByteArray(Charsets.US_ASCII).copyInto(it) }
        assertTrue(runCatching { SyncEnvelopeCrypto.decrypt(unknown, fixedSecret) }.isFailure)
    }

    @Test fun anUnknownLabelOrVersionIsRejectedBeforeAnyKeyDerivation() {
        // The GCM tag would reject these too, but only AFTER an old-style key derivation (about 1.5 s on a phone): a junk envelope must not cost that.
        fun fastest(block: () -> Unit): Long { block(); return (1..3).minOf { val t = System.nanoTime(); block(); System.nanoTime() - t } }
        val oneOldDerivation = fastest { SyncEnvelopeCrypto.decrypt(frozenRcs1, fixedSecret) }
        val unknownLabel = frozenRcs1.copyOf().also { "RCS9".toByteArray(Charsets.US_ASCII).copyInto(it) }
        val rcs1LabelWrongVersion = frozenRcs1.copyOf().also { it[SyncEnvelopeCrypto.VERSION_OFFSET] = 9 }
        val rcs2LabelWrongVersion = frozenRcs2.copyOf().also { it[SyncEnvelopeCrypto.VERSION_OFFSET] = 9 }
        listOf(unknownLabel, rcs1LabelWrongVersion, rcs2LabelWrongVersion).forEach { junk ->
            var failure: Throwable? = null
            val rejected = fastest { failure = runCatching { SyncEnvelopeCrypto.decrypt(junk, fixedSecret) }.exceptionOrNull() }
            assertTrue(failure is IllegalArgumentException)
            assertTrue("rejected in ${rejected / 1_000} us; one old derivation took ${oneOldDerivation / 1_000} us", rejected * MIN_SPEEDUP <= oneOldDerivation)
        }
    }

    @Test fun theRcs2KeyDependsOnTheSaltAndOnTheSecret() {
        val first = SyncEnvelopeCrypto.encrypt(plain, secret)
        val second = SyncEnvelopeCrypto.encrypt(plain, secret)
        // Same secret, new salt and nonce: the whole envelope differs, and each one opens only with its own header.
        assertArrayEquals(plain, SyncEnvelopeCrypto.decrypt(first, secret))
        assertArrayEquals(plain, SyncEnvelopeCrypto.decrypt(second, secret))
        val swapped = first.copyOf().also { second.copyInto(it, SyncEnvelopeCrypto.SALT_OFFSET, SyncEnvelopeCrypto.SALT_OFFSET, SyncEnvelopeCrypto.NONCE_OFFSET) }
        assertTrue("a different salt must give a different key", runCatching { SyncEnvelopeCrypto.decrypt(swapped, secret) }.isFailure)
    }

    @Test fun rcs2KeyDerivationIsFarFasterThanTheOldPbkdf2() {
        // A ratio, not a number of milliseconds: it holds on a slow machine too. Measured on a dev machine: about 60,000 times.
        fun fastest(runs: Int, block: () -> Unit): Long { repeat(2) { block() }; return (1..runs).minOf { val t = System.nanoTime(); block(); System.nanoTime() - t } }
        val old = fastest(3) { SyncEnvelopeCrypto.decrypt(frozenRcs1, fixedSecret) }
        val new = fastest(3) { SyncEnvelopeCrypto.decrypt(frozenRcs2, fixedSecret) }
        assertTrue("RCS1 ${old / 1_000} us, RCS2 ${new / 1_000} us", old >= new * MIN_SPEEDUP)
    }

    private companion object { const val MIN_SPEEDUP = 20 }
}
