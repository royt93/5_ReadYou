package com.mckimquyen.reader.infrastructure.mirror

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import com.mckimquyen.reader.domain.mirror.LanAddress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/** Where the mirror learns which local address it may bind to, and when that address goes away. */
interface LanAddressSource {
    /** A private IPv4 address of the active Wi-Fi or Ethernet network, or null when there is none. */
    fun current(): InetAddress?

    /** Emits once whenever the active network is lost or its address changes. */
    fun networkLosses(): Flow<Unit>
}

/**
 * Reads the address from `LinkProperties`, which needs only ACCESS_NETWORK_STATE (already declared). The older
 * `WifiManager.connectionInfo.ipAddress` route is deprecated and would need an extra permission.
 */
@Singleton
class LanAddressProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LanAddressSource {

    override fun current(): InetAddress? {
        val manager = context.getSystemService<ConnectivityManager>() ?: return null
        val network = manager.activeNetwork ?: return null
        val capabilities = manager.getNetworkCapabilities(network) ?: return null
        val local = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!local) return null
        return privateAddress(manager.getLinkProperties(network))
    }

    override fun networkLosses(): Flow<Unit> = callbackFlow {
        val manager = context.getSystemService<ConnectivityManager>()
        if (manager == null) {
            close()
            return@callbackFlow
        }
        var lastAddress: String? = current()?.hostAddress
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                lastAddress = null
                trySend(Unit)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                val now = privateAddress(linkProperties)?.hostAddress
                if (now != lastAddress) trySend(Unit)
                lastAddress = now
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }

    private fun privateAddress(properties: LinkProperties?): InetAddress? {
        val candidates = properties?.linkAddresses.orEmpty()
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
        return LanAddress.pick(candidates)?.let(InetAddress::getByName)
    }
}
