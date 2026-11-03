package com.mckimquyen.reader.domain.mirror

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SyncSessionRouterTest {
    private val token = MirrorToken("a".repeat(32), 5_000L)
    private val ciphertext = byteArrayOf(0, 1, 2, -1)
    private val router = SyncSessionRouter({ token }, { ciphertext })

    @Test fun validExactPathReturnsOnlyCiphertext() {
        val response = router.route(MirrorRequest("/s/${token.value}"), 4_999L)
        assertArrayEquals(ciphertext, (response as SyncSessionResponse.Ciphertext).bytes)
    }

    @Test fun wrongExpiredAndNonSyncPathsAreIndistinguishable() {
        listOf(
            MirrorRequest("/s/${"b".repeat(32)}"),
            MirrorRequest("/s/${token.value}"),
            MirrorRequest("/s/${token.value}/state"),
            MirrorRequest("/m/${token.value}/state"),
            MirrorRequest("/s/${token.value}?x=1"),
        ).forEachIndexed { index, request ->
            val now = if (index == 1) 5_000L else 4_999L
            assertSame(SyncSessionResponse.NotFound, router.route(request, now))
        }
    }
}
