package com.mckimquyen.reader.domain.p2psync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncSessionUrlTest {
    @Test fun parsesOnlyExactPrivateLanHttpSessionUrls() {
        val parsed = SyncSessionUrl.parse("http://192.168.1.9:43210/s/${"a".repeat(32)}")
        assertEquals("192.168.1.9", parsed?.host)
        assertEquals(43210, parsed?.port)
        assertEquals("/s/${"a".repeat(32)}", parsed?.path)
    }

    @Test fun rejectsPublicHostHttpsUserInfoQueryFragmentAndBadPath() {
        val token = "a".repeat(32)
        listOf(
            "http://example.com:1234/s/$token",
            "http://8.8.8.8:1234/s/$token",
            "https://192.168.1.9:1234/s/$token",
            "http://u:p@192.168.1.9:1234/s/$token",
            "http://192.168.1.9:1234/s/$token?x=1",
            "http://192.168.1.9:1234/s/$token#fragment",
            "http://192.168.1.9:1234/m/$token/state",
            "http://192.168.1.9:1234/s/${"A".repeat(32)}",
        ).forEach { assertNull(it, SyncSessionUrl.parse(it)) }
    }

    @Test fun rejectsInvalidPortsControlsAndOversizeText() {
        val token = "b".repeat(32)
        listOf(
            "http://192.168.1.9:0/s/$token",
            "http://192.168.1.9:65536/s/$token",
            "http://192.168.1.9/s/$token",
            "http://192.168.1.9:12/s/$token\n",
            "http://192.168.1.9:12/s/$token" + "x".repeat(500),
        ).forEach { assertNull(it, SyncSessionUrl.parse(it)) }
    }
}
