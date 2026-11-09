package com.mckimquyen.reader.domain.p2psync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NsdTxtCodecTest {
    private val nonce = ByteArray(16) { (it * 3).toByte() }

    private fun text(bytes: ByteArray?) = bytes?.toString(Charsets.US_ASCII)

    @Test fun serviceTypeIsTheAgreedOne() {
        assertEquals("_rsscathub._tcp", NsdTxtCodec.SERVICE_TYPE)
    }

    @Test fun encodeCarriesExactlyTheVersionAndTheNonce() {
        val attributes = NsdTxtCodec.encode(nonce)
        assertEquals(setOf("v", "n"), attributes.keys)
        assertEquals("1", text(attributes["v"]))
        assertEquals(SessionTokenDerivation.nonceHex(nonce), text(attributes["n"]))
    }

    @Test fun encodeNeverCarriesATokenOrASecretField() {
        val keys = NsdTxtCodec.encode(nonce).keys.map { it.lowercase() }
        assertFalse(keys.any { "token" in it || "secret" in it || "name" in it || "device" in it })
    }

    @Test fun decodeReturnsTheNonceForAWellFormedRecord() {
        assertArrayEquals(nonce, NsdTxtCodec.decode(NsdTxtCodec.encode(nonce)))
    }

    @Test fun decodeRejectsAnUnknownKey() {
        val withExtra = NsdTxtCodec.encode(nonce) + ("name" to "Roy's phone".toByteArray())
        assertNull(NsdTxtCodec.decode(withExtra))
    }

    @Test fun decodeRejectsAMissingNonceOrVersion() {
        assertNull(NsdTxtCodec.decode(mapOf("v" to "1".toByteArray())))
        assertNull(NsdTxtCodec.decode(mapOf("n" to SessionTokenDerivation.nonceHex(nonce).toByteArray())))
        assertNull(NsdTxtCodec.decode(emptyMap()))
    }

    @Test fun decodeRejectsAnotherVersion() {
        val v2 = mapOf("v" to "2".toByteArray(), "n" to SessionTokenDerivation.nonceHex(nonce).toByteArray())
        assertNull(NsdTxtCodec.decode(v2))
    }

    @Test fun decodeRejectsABadNonce() {
        val short = mapOf("v" to "1".toByteArray(), "n" to "ab".repeat(15).toByteArray())
        val upper = mapOf("v" to "1".toByteArray(), "n" to "AB".repeat(16).toByteArray())
        val nonHex = mapOf("v" to "1".toByteArray(), "n" to "zz".repeat(16).toByteArray())
        listOf(short, upper, nonHex).forEach { assertNull(NsdTxtCodec.decode(it)) }
    }

    @Test fun decodeToleratesNullValuesWithoutThrowing() {
        assertNull(NsdTxtCodec.decode(mapOf("v" to null, "n" to null)))
        assertTrue(runCatching { NsdTxtCodec.decode(mapOf("v" to "1".toByteArray(), "n" to null)) }.isSuccess)
    }
}
