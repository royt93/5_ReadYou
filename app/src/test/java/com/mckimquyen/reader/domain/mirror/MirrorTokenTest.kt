package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class MirrorTokenTest {
    private val now = 1_000_000L

    @Test fun generate_makesHexOfTokenBytesTimesTwo() {
        val token = MirrorToken.generate(now)
        assertEquals(MirrorLimits.TOKEN_BYTES * 2, token.value.length)
        assertTrue(token.value.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun generate_isDifferentEveryCall() {
        assertEquals(200, (1..200).map { MirrorToken.generate(now).value }.toSet().size)
    }

    @Test fun generate_usesTheGivenRandomSourceAndTtl() {
        val fixed = object : SecureRandom() { override fun nextBytes(bytes: ByteArray) { bytes.fill(0xAB.toByte()) } }
        val token = MirrorToken.generate(now, fixed)
        assertEquals("ab".repeat(MirrorLimits.TOKEN_BYTES), token.value)
        assertEquals(now + MirrorLimits.TOKEN_TTL_MILLIS, token.expiresAtMillis)
    }

    @Test fun matches_trueOnlyForTheExactValueBeforeExpiry() {
        val token = MirrorToken.generate(now)
        assertTrue(token.matches(token.value, now))
        assertTrue(token.matches(token.value, token.expiresAtMillis - 1))
        assertFalse(token.matches(token.value, token.expiresAtMillis))
        assertFalse(token.matches(token.value, token.expiresAtMillis + 1))
    }

    @Test fun matches_falseForNullEmptyShorterLongerAndChangedCase() {
        val token = MirrorToken("abcdef0123456789abcdef0123456789", now + 10)
        assertFalse(token.matches(null, now))
        assertFalse(token.matches("", now))
        assertFalse(token.matches("abcdef", now))
        assertFalse(token.matches(token.value + "0", now))
        assertFalse(token.matches(token.value.uppercase(), now))
        assertFalse(token.matches("abcdef0123456789abcdef012345678a", now))
    }
}
