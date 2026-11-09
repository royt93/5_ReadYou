package com.mckimquyen.reader.infrastructure.p2psync

import com.mckimquyen.reader.domain.mirror.LanAddress
import com.mckimquyen.reader.domain.p2psync.NsdTxtCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.SecureRandom

class DiscoveredPeer(val serviceName: String, val host: String, val port: Int, val nonce: ByteArray)

/**
 * Advertises one pairing session and finds peers on the LAN. Results are accepted only for private IPv4 hosts with a
 * well-formed record, and everything is torn down by [stop].
 */
class PeerDiscovery(private val platform: NsdPlatform) {
    private val lock = Any()
    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()
    private var advertising = false
    private var discovering = false

    /** The service name is random per session so it cannot be used to follow a device from one session to the next. */
    fun advertise(port: Int, nonce: ByteArray): Boolean = synchronized(lock) {
        if (advertising) return@synchronized false
        var accepted = false
        platform.register(randomServiceName(), port, NsdTxtCodec.encode(nonce)) { accepted = it }
        advertising = accepted
        accepted
    }

    fun startDiscovery() = synchronized(lock) {
        if (discovering) return@synchronized
        discovering = true
        platform.discover(
            onFound = { name, host, port, attributes -> onFound(name, host, port, attributes) },
            onLost = { name -> if (discovering) _peers.update { list -> list.filterNot { it.serviceName == name } } },
            onFailed = { synchronized(lock) { discovering = false } },
        )
    }

    fun stop() = synchronized(lock) {
        if (advertising) { platform.unregister(); advertising = false }
        if (discovering) { platform.stopDiscovery(); discovering = false }
        _peers.value = emptyList()
    }

    private fun onFound(name: String, host: String?, port: Int, attributes: Map<String, ByteArray?>) {
        if (!discovering) return
        if (host == null || !LanAddress.isPrivateIpv4(host)) return
        val nonce = NsdTxtCodec.decode(attributes) ?: return
        val peer = DiscoveredPeer(name, host, port, nonce)
        _peers.update { list -> list.filterNot { it.serviceName == name } + peer }
    }

    private fun randomServiceName(): String {
        val bytes = ByteArray(SERVICE_NAME_BYTES).also(SecureRandom()::nextBytes)
        return SERVICE_NAME_PREFIX + bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val SERVICE_NAME_PREFIX = "rsscathub-"
        const val SERVICE_NAME_BYTES = 6
    }
}
