package com.mckimquyen.reader.domain.model.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FullContentPolicyTest {
    @Test fun nullAndEmptyRemainDistinct() {
        assertNull(FullContentPolicy.accept(null))
        assertEquals("", FullContentPolicy.accept(""))
    }

    @Test fun exactAsciiByteLimitIsAcceptedByteIdentically() {
        val content = "x".repeat(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES)
        assertEquals(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES, FullContentPolicy.byteSize(content))
        assertEquals(content, FullContentPolicy.accept(content))
    }

    @Test fun oneByteOverAsciiLimitIsRejected() {
        val content = "x".repeat(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES + 1)
        assertNull(FullContentPolicy.accept(content))
    }

    @Test fun measuresUtf8BytesAndPreservesMultibyteBoundary() {
        val threeByteSuffix = "漢".repeat(THREE_BYTE_CHARACTERS)
        val exactlyLimit = "x".repeat(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES - threeByteSuffix.toByteArray(Charsets.UTF_8).size) + threeByteSuffix
        assertEquals(FullContentPolicy.MAX_STORED_FULL_CONTENT_BYTES, FullContentPolicy.byteSize(exactlyLimit))
        assertEquals(exactlyLimit, FullContentPolicy.accept(exactlyLimit))
        assertNull(FullContentPolicy.accept(exactlyLimit + "x"))
    }

    @Test fun emojiAndCombiningSequenceAreMeasuredAfterUtf8Encoding() {
        val text = "🙂é"
        assertEquals(text.toByteArray(Charsets.UTF_8).size, FullContentPolicy.byteSize(text))
        assertEquals(text, FullContentPolicy.accept(text))
    }

    private companion object { const val THREE_BYTE_CHARACTERS = 3 }
}
