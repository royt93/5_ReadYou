package com.mckimquyen.reader.domain.p2psync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class SessionTokenDerivationTest {
    private val secretA = PairingSecret.fromHex("00112233445566778899aabbccddeeff")
    private val secretB = PairingSecret.fromHex("ffeeddccbbaa99887766554433221100")
    private val nonce1 = ByteArray(16) { it.toByte() }
    private val nonce2 = ByteArray(16) { (it + 1).toByte() }

    @Test fun tokenIs32LowercaseHexCharacters() {
        val token = SessionTokenDerivation.derive(secretA, nonce1)
        assertEquals(32, token.length)
        assertTrue(token.all { it in "0123456789abcdef" })
    }

    @Test fun sameSecretAndNonceAlwaysGiveTheSameToken() {
        assertEquals(SessionTokenDerivation.derive(secretA, nonce1), SessionTokenDerivation.derive(secretA, nonce1))
    }

    @Test fun differentNonceGivesDifferentToken() {
        assertNotEquals(SessionTokenDerivation.derive(secretA, nonce1), SessionTokenDerivation.derive(secretA, nonce2))
    }

    @Test fun differentSecretGivesDifferentToken() {
        assertNotEquals(SessionTokenDerivation.derive(secretA, nonce1), SessionTokenDerivation.derive(secretB, nonce1))
    }

    @Test fun tokenDoesNotContainTheSecretOrTheNonce() {
        val token = SessionTokenDerivation.derive(secretA, nonce1)
        assertFalse(token.contains(secretA.hex))
        assertFalse(token.contains(SessionTokenDerivation.nonceHex(nonce1)))
    }

    @Test fun knownAnswerPinsTheDerivationSoItCannotDriftSilently() {
        // Independent Python hmac: key is the decoded 16 secret bytes, not its 32-byte hex text.
        assertEquals("119c7584d5a38d54d4575d4987b96220", SessionTokenDerivation.derive(secretA, nonce1))
    }

    @Test fun newNonceHasTheRightLengthAndDiffersEachTime() {
        val random = SecureRandom()
        val first = SessionTokenDerivation.newNonce(random)
        val second = SessionTokenDerivation.newNonce(random)
        assertEquals(16, first.size)
        assertNotEquals(SessionTokenDerivation.nonceHex(first), SessionTokenDerivation.nonceHex(second))
    }

    @Test fun nonceHexRoundTrips() {
        assertEquals(nonce1.toList(), SessionTokenDerivation.nonceFromHex(SessionTokenDerivation.nonceHex(nonce1))?.toList())
    }

    @Test fun nonceFromHexRejectsWrongLengthAndNonHex() {
        assertNull(SessionTokenDerivation.nonceFromHex(""))
        assertNull(SessionTokenDerivation.nonceFromHex("00".repeat(15)))
        assertNull(SessionTokenDerivation.nonceFromHex("00".repeat(17)))
        assertNull(SessionTokenDerivation.nonceFromHex("zz".repeat(16)))
        assertNull(SessionTokenDerivation.nonceFromHex("AA".repeat(16)))
    }

    @Test fun matchesAcceptsOnlyTheExactToken() {
        val token = SessionTokenDerivation.derive(secretA, nonce1)
        assertTrue(SessionTokenDerivation.matches(token, token))
        assertFalse(SessionTokenDerivation.matches(token, null))
        assertFalse(SessionTokenDerivation.matches(token, token.dropLast(1)))
        assertFalse(SessionTokenDerivation.matches(token, token.dropLast(1) + (if (token.last() == 'a') 'b' else 'a')))
    }
}
