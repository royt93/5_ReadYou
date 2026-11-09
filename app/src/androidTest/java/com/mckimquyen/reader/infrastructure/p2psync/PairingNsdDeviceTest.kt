package com.mckimquyen.reader.infrastructure.p2psync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.mirror.LanAddress
import com.mckimquyen.reader.domain.p2psync.SessionTokenDerivation
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket

/**
 * Real NsdManager on the device LAN. Needs ACCESS_LOCAL_NETWORK granted to the app (API 37); the runner grants and
 * revokes it around the run. The service advertises a throw-away port and then discovers itself.
 */
@RunWith(AndroidJUnit4::class)
class PairingNsdDeviceTest {
    private lateinit var discovery: PeerDiscovery
    private lateinit var socket: ServerSocket

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        discovery = PeerDiscovery(AndroidNsdPlatform(context))
        socket = ServerSocket(0)
    }

    @After fun tearDown() {
        discovery.stop()
        socket.close()
    }

    private fun waitFor(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(POLL_MILLIS)
        }
        return condition()
    }

    @Test fun advertisedServiceIsFoundWithItsNonceAndAPrivateHost() {
        val nonce = SessionTokenDerivation.newNonce()
        discovery.startDiscovery()
        assertTrue("advertise must report success", discovery.advertise(socket.localPort, nonce))
        val found = waitFor(DISCOVER_TIMEOUT_MILLIS) { discovery.peers.value.any { it.port == socket.localPort } }
        assertTrue("service was not discovered; peers=${discovery.peers.value.map { it.serviceName }}", found)
        val peer = discovery.peers.value.first { it.port == socket.localPort }
        assertArrayEquals(nonce, peer.nonce)
        assertTrue("host ${peer.host} must be private IPv4", LanAddress.isPrivateIpv4(peer.host))
    }

    @Test fun stopClearsPeersAndAllowsAdvertisingAgain() {
        discovery.startDiscovery()
        assertTrue(discovery.advertise(socket.localPort, SessionTokenDerivation.newNonce()))
        waitFor(DISCOVER_TIMEOUT_MILLIS) { discovery.peers.value.isNotEmpty() }
        discovery.stop()
        assertEquals(emptyList<DiscoveredPeer>(), discovery.peers.value)
        assertTrue("advertise must work again after stop", discovery.advertise(socket.localPort, SessionTokenDerivation.newNonce()))
    }

    private companion object {
        const val POLL_MILLIS = 250L
        const val DISCOVER_TIMEOUT_MILLIS = 20_000L
    }
}
