package com.mckimquyen.reader.infrastructure.p2psync

import com.mckimquyen.reader.domain.p2psync.NsdTxtCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The registered TXT values go through the String overload of NsdServiceInfo.setAttribute, so the String must carry the SAME bytes. */
class AsciiTxtValueTest {
    @Test
    fun theCodecsOwnValues_becomeTextWithTheSameBytes() {
        val encoded = NsdTxtCodec.encode(ByteArray(16) { (it * 7).toByte() })
        encoded.values.forEach { bytes ->
            val text = requireNotNull(asciiTxtValue(bytes))
            assertArrayEquals(bytes, text.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun plainText_isReturnedAsIs() = assertEquals("abc-123", asciiTxtValue("abc-123".toByteArray(Charsets.US_ASCII)))

    @Test
    fun emptyValue_isAnEmptyText() = assertEquals("", asciiTxtValue(ByteArray(0)))

    @Test
    fun aByteAbove127_isRefused_becauseTheTextFormWouldChangeTheBytes() {
        assertNull(asciiTxtValue(byteArrayOf(0x61, 0xFF.toByte())))
    }

    @Test
    fun aNegativeByte_isRefused() = assertNull(asciiTxtValue(byteArrayOf(-1)))
}
