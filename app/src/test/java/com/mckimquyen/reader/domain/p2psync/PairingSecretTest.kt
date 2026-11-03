package com.mckimquyen.reader.domain.p2psync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PairingSecretTest {
    @Test fun secretIs128BitLowerHex() {
        val secret = PairingSecret.generate()
        assertEquals(32, secret.hex.length)
        assertTrue(secret.hex.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun eachSessionUsesFreshRandomBytes() {
        assertEquals(200, (1..200).map { PairingSecret.generate().hex }.toSet().size)
    }

    @Test fun injectedRandomMakesSecretReproducible() {
        val random = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) { bytes.fill(0xAB.toByte()) }
        }
        assertEquals("ab".repeat(16), PairingSecret.generate(random).hex)
    }

    @Test fun parserRejectsWrongLengthCaseAndNonHex() {
        listOf("", "a", "0".repeat(31), "0".repeat(33), "A".repeat(32), "g".repeat(32)).forEach {
            assertTrue(runCatching { PairingSecret.fromHex(it) }.isFailure)
        }
    }
}
