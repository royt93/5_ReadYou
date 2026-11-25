package com.mckimquyen.reader.infrastructure.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfSignedTrustTest {
    private class MemoryStore(var hosts: Set<String> = emptySet(), var writes: Int = 0) : SelfSignedTrust.Store {
        override fun read() = hosts
        override fun write(hosts: Set<String>) {
            this.hosts = hosts
            writes++
        }
    }

    private val store = MemoryStore()
    private val trust = SelfSignedTrust(store)

    @Test fun nothingIsTrustedByDefault() {
        assertFalse(trust.isTrusted("https://nextcloud.home.lan/remote.php/dav"))
        assertFalse(trust.isTrusted(null))
        assertFalse(trust.isTrusted(""))
    }

    @Test fun trustIsPerHostNotGlobal() {
        trust.setTrusted("https://nextcloud.home.lan/remote.php/dav", true)
        assertTrue(trust.isTrusted("https://nextcloud.home.lan/other/path"))
        assertFalse("another host is not covered", trust.isTrusted("https://evil.example.com/remote.php/dav"))
        assertFalse("a sub domain is not covered either", trust.isTrusted("https://a.nextcloud.home.lan/"))
    }

    @Test fun thePortIsPartOfTheKey() {
        trust.setTrusted("https://server.lan:8443/api", true)
        assertTrue(trust.isTrusted("https://server.lan:8443/x"))
        assertFalse(trust.isTrusted("https://server.lan/x"))
        assertFalse(trust.isTrusted("https://server.lan:9443/x"))
    }

    @Test fun hostNamesAreCaseInsensitiveAndSchemeIsOptional() {
        trust.setTrusted("HTTPS://Server.LAN/Path", true)
        assertTrue(trust.isTrusted("server.lan"))
        assertTrue(trust.isTrusted("https://SERVER.lan"))
    }

    @Test fun trustCanBeSwitchedOffAgain() {
        trust.setTrusted("https://server.lan", true)
        trust.setTrusted("https://server.lan", false)
        assertFalse(trust.isTrusted("https://server.lan"))
    }

    @Test fun anUnusableUrlNeverCreatesAnException() {
        trust.setTrusted("not a url ::", true)
        trust.setTrusted("", true)
        trust.setTrusted(null, true)
        assertEquals(emptySet<String>(), store.hosts)
        assertEquals(0, store.writes)
    }

    @Test fun settingTheSameValueDoesNotWriteAgain() {
        trust.setTrusted("https://server.lan", true)
        trust.setTrusted("https://server.lan", true)
        assertEquals(1, store.writes)
    }

    @Test fun keyOfNormalisesHostAndPort() {
        assertEquals("server.lan", SelfSignedTrust.keyOf("https://server.lan/a/b"))
        assertEquals("server.lan:8443", SelfSignedTrust.keyOf("https://Server.Lan:8443/a"))
        assertNull(SelfSignedTrust.keyOf("   "))
    }
}
