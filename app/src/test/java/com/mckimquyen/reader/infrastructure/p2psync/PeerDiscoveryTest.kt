package com.mckimquyen.reader.infrastructure.p2psync

import com.mckimquyen.reader.domain.p2psync.NsdTxtCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeNsd : NsdPlatform {
    var registered: Triple<String, Int, Map<String, ByteArray>>? = null
    var registerResult = true
    /** When true the result is delivered later through [deliverRegisterResult], like the real NsdManager does. */
    var deferRegisterResult = false
    private val pendingResults = mutableListOf<(Boolean) -> Unit>()
    var unregisterCalls = 0
    var discoverCalls = 0
    var stopDiscoveryCalls = 0
    private var onFound: ((String, String?, Int, Map<String, ByteArray?>) -> Unit)? = null
    private var onLost: ((String) -> Unit)? = null

    override fun register(serviceName: String, port: Int, attributes: Map<String, ByteArray>, onResult: (Boolean) -> Unit) {
        registered = Triple(serviceName, port, attributes)
        if (deferRegisterResult) pendingResults += onResult else onResult(registerResult)
    }

    override fun unregister() { unregisterCalls++; registered = null }

    override fun discover(
        onFound: (String, String?, Int, Map<String, ByteArray?>) -> Unit,
        onLost: (String) -> Unit,
        onFailed: () -> Unit,
    ) { discoverCalls++; this.onFound = onFound; this.onLost = onLost }

    // Real NsdManager can still deliver a callback that was already queued after stopDiscovery(), so keep the
    // listeners and let tests fire late results.
    override fun stopDiscovery() { stopDiscoveryCalls++ }

    fun found(name: String, host: String?, port: Int, attrs: Map<String, ByteArray?>) = onFound?.invoke(name, host, port, attrs)
    fun lost(name: String) = onLost?.invoke(name)
    fun deliverRegisterResult(index: Int, ok: Boolean) = pendingResults[index](ok)
}

class PeerDiscoveryTest {
    private val nsd = FakeNsd()
    private val discovery = PeerDiscovery(nsd)
    private val nonce = ByteArray(16) { it.toByte() }
    private val goodAttrs: Map<String, ByteArray?> = NsdTxtCodec.encode(nonce)

    @Test fun advertiseRegistersTheServiceWithOnlyTheNonceAndTheVersion() {
        assertTrue(discovery.advertise(4321, nonce))
        val (_, port, attrs) = nsd.registered!!
        assertEquals(4321, port)
        assertEquals(setOf("v", "n"), attrs.keys)
    }

    @Test fun theServiceNameIsRandomPerSessionAndCarriesNoDeviceIdentity() {
        discovery.advertise(1, nonce); val first = nsd.registered!!.first
        discovery.stop()
        discovery.advertise(1, nonce); val second = nsd.registered!!.first
        assertNotEquals(first, second)
        listOf(first, second).forEach { name ->
            assertTrue(name, name.startsWith("rsscathub-"))
            assertFalse(name, name.lowercase().let { "token" in it || "secret" in it || "phone" in it || "pixel" in it || "samsung" in it })
        }
    }

    @Test fun advertiseDoesNotWaitForTheAsynchronousRegistrationResult() {
        nsd.deferRegisterResult = true
        assertTrue(discovery.advertise(1, nonce))
    }

    @Test fun stopUnregistersAServiceWhoseResultHasNotArrivedYet() {
        nsd.deferRegisterResult = true
        discovery.advertise(1, nonce)
        discovery.stop()
        assertEquals(1, nsd.unregisterCalls)
    }

    @Test fun aLateRegistrationFailureAllowsAdvertisingAgain() {
        nsd.deferRegisterResult = true
        discovery.advertise(1, nonce)
        nsd.deliverRegisterResult(0, false)
        assertTrue(discovery.advertise(2, nonce))
        assertEquals(2, nsd.registered!!.second)
    }

    @Test fun aLateFailureFromAnOldSessionDoesNotAffectTheNewOne() {
        nsd.deferRegisterResult = true
        discovery.advertise(1, nonce)
        discovery.stop()
        discovery.advertise(2, nonce)
        nsd.deliverRegisterResult(0, false)
        assertFalse(discovery.advertise(3, nonce))
    }

    @Test fun advertiseReportsFailureWhenThePlatformRefuses() {
        nsd.registerResult = false
        assertFalse(discovery.advertise(1, nonce))
    }

    @Test fun aPrivateIpv4HostIsKeptAndPublicOrLoopbackHostsAreDropped() {
        discovery.startDiscovery()
        nsd.found("a", "192.168.1.20", 80, goodAttrs)
        nsd.found("b", "10.0.0.5", 81, goodAttrs)
        nsd.found("c", "8.8.8.8", 82, goodAttrs)
        nsd.found("d", "127.0.0.1", 83, goodAttrs)
        nsd.found("e", "169.254.1.1", 84, goodAttrs)
        nsd.found("f", "fe80::1", 85, goodAttrs)
        nsd.found("g", null, 86, goodAttrs)
        assertEquals(listOf("a", "b"), discovery.peers.value.map { it.serviceName }.sorted())
    }

    @Test fun aRecordWithBadTxtIsIgnoredWithoutThrowing() {
        discovery.startDiscovery()
        nsd.found("bad", "192.168.1.20", 80, mapOf("v" to "1".toByteArray(), "n" to "nothex".toByteArray()))
        nsd.found("extra", "192.168.1.21", 80, goodAttrs + ("name" to "x".toByteArray()))
        assertTrue(discovery.peers.value.isEmpty())
    }

    @Test fun theSameServiceTwiceDoesNotDuplicateAndAnUpdateReplacesIt() {
        discovery.startDiscovery()
        nsd.found("a", "192.168.1.20", 80, goodAttrs)
        nsd.found("a", "192.168.1.22", 99, goodAttrs)
        val peers = discovery.peers.value
        assertEquals(1, peers.size)
        assertEquals("192.168.1.22", peers.single().host)
        assertEquals(99, peers.single().port)
    }

    @Test fun aLostServiceDisappearsFromTheList() {
        discovery.startDiscovery()
        nsd.found("a", "192.168.1.20", 80, goodAttrs)
        nsd.lost("a")
        assertTrue(discovery.peers.value.isEmpty())
    }

    @Test fun theDiscoveredNonceIsDecodedFromTheRecord() {
        discovery.startDiscovery()
        nsd.found("a", "192.168.1.20", 80, goodAttrs)
        assertEquals(nonce.toList(), discovery.peers.value.single().nonce.toList())
    }

    @Test fun stopUnregistersAndStopsDiscoveryAndClearsThePeers() {
        discovery.advertise(1, nonce)
        discovery.startDiscovery()
        nsd.found("a", "192.168.1.20", 80, goodAttrs)
        discovery.stop()
        assertEquals(1, nsd.unregisterCalls)
        assertEquals(1, nsd.stopDiscoveryCalls)
        assertTrue(discovery.peers.value.isEmpty())
    }

    @Test fun stopIsSafeWhenNothingWasStartedAndWhenCalledTwice() {
        discovery.stop(); discovery.stop()
        assertEquals(0, nsd.unregisterCalls)
        assertEquals(0, nsd.stopDiscoveryCalls)
    }

    @Test fun startingDiscoveryTwiceDoesNotRegisterTwoListeners() {
        discovery.startDiscovery(); discovery.startDiscovery()
        assertEquals(1, nsd.discoverCalls)
    }

    @Test fun resultsAfterStopAreIgnored() {
        discovery.startDiscovery()
        discovery.stop()
        nsd.found("late", "192.168.1.20", 80, goodAttrs)
        assertTrue(discovery.peers.value.isEmpty())
    }
}
