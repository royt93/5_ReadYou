package com.mckimquyen.reader.infrastructure.p2psync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.mckimquyen.reader.domain.p2psync.NsdTxtCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real `NsdManager` adapter, written against the API levels in the SDK's api-versions.xml (minSdk is 26):
 * - `registerService`, `discoverServices` and `resolveService` have an `Executor` overload only from API 33; below
 *   that the older overload (callbacks on NsdManager's own thread) is used.
 * - A found service is resolved with `registerServiceInfoCallback(NsdServiceInfo, ...)` from API 34 (the replacement
 *   for the deprecated `resolveService`), and with `resolveService` below that.
 * - The `DiscoveryRequest` overloads (API 35 and 37) are deliberately not used.
 *
 * UNVERIFIED ON A DEVICE: this class is compiled and its logic sits behind [NsdPlatform], but it has not been run
 * against a real network yet.
 */
@Singleton
class AndroidNsdPlatform @Inject constructor(
    @ApplicationContext context: Context,
) : NsdPlatform {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val executor: Executor = ContextCompat.getMainExecutor(context)
    private val lock = Any()
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val infoCallbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()
    private val resolveListeners = mutableMapOf<String, NsdManager.ResolveListener>()

    override fun register(serviceName: String, port: Int, attributes: Map<String, ByteArray>, onResult: (Boolean) -> Unit) {
        val nsd = manager ?: return onResult(false)
        synchronized(lock) {
            if (registration != null) return onResult(false)
            val info = NsdServiceInfo().apply {
                this.serviceName = serviceName
                serviceType = NsdTxtCodec.SERVICE_TYPE
                this.port = port
                attributes.forEach { (key, value) -> setAttribute(key, value.copyOf()) }
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(lock) { if (registration === this) registration = null }
                    onResult(false)
                }
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    // unregister() may have run before the registration finished; do not leave that service published.
                    val stale = synchronized(lock) { registration !== this }
                    if (stale) runCatching { nsd.unregisterService(this) } else onResult(true)
                }
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    synchronized(lock) { if (registration === this) registration = null }
                }
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(lock) { if (registration === this) registration = null }
                }
            }
            registration = listener
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, executor, listener)
                } else {
                    nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
                }
            } catch (e: RuntimeException) {
                registration = null
                onResult(false)
            }
        }
    }

    override fun unregister() {
        val nsd = manager ?: return
        val listener = synchronized(lock) { registration.also { registration = null } } ?: return
        runCatching { nsd.unregisterService(listener) }
    }

    override fun discover(
        onFound: (serviceName: String, host: String?, port: Int, attributes: Map<String, ByteArray?>) -> Unit,
        onLost: (serviceName: String) -> Unit,
        onFailed: () -> Unit,
    ) {
        val nsd = manager ?: return onFailed()
        synchronized(lock) {
            if (discovery != null) return onFailed()
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    synchronized(lock) { if (discovery === this) discovery = null }
                    onFailed()
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    synchronized(lock) { if (discovery === this) discovery = null }
                }
                override fun onServiceFound(serviceInfo: NsdServiceInfo) = resolve(nsd, serviceInfo, onFound)
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    stopResolving(nsd, serviceInfo.serviceName)
                    onLost(serviceInfo.serviceName)
                }
            }
            discovery = listener
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    nsd.discoverServices(NsdTxtCodec.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, null as android.net.Network?, executor, listener)
                } else {
                    nsd.discoverServices(NsdTxtCodec.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                }
            } catch (e: RuntimeException) {
                discovery = null
                onFailed()
            }
        }
    }

    private fun resolve(
        nsd: NsdManager,
        found: NsdServiceInfo,
        onFound: (String, String?, Int, Map<String, ByteArray?>) -> Unit,
    ) {
        val name = found.serviceName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceUpdated(info: NsdServiceInfo) {
                    onFound(info.serviceName, info.hostAddresses.firstOrNull()?.hostAddress, info.port, copy(info.attributes))
                }
                override fun onServiceLost() = Unit
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    synchronized(lock) { infoCallbacks.remove(name) }
                }
                override fun onServiceInfoCallbackUnregistered() {
                    synchronized(lock) { infoCallbacks.remove(name, this) }
                }
            }
            synchronized(lock) { if (infoCallbacks.containsKey(name)) return; infoCallbacks[name] = callback }
            try {
                nsd.registerServiceInfoCallback(found, executor, callback)
            } catch (e: RuntimeException) {
                synchronized(lock) { infoCallbacks.remove(name, callback) }
            }
        } else {
            resolveLegacy(nsd, found, onFound)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveLegacy(
        nsd: NsdManager,
        found: NsdServiceInfo,
        onFound: (String, String?, Int, Map<String, ByteArray?>) -> Unit,
    ) {
        val name = found.serviceName
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                synchronized(lock) { resolveListeners.remove(name) }
            }
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                synchronized(lock) { resolveListeners.remove(name) }
                onFound(resolved.serviceName, resolved.host?.hostAddress, resolved.port, copy(resolved.attributes))
            }
        }
        synchronized(lock) { if (resolveListeners.containsKey(name)) return; resolveListeners[name] = listener }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                nsd.resolveService(found, executor, listener)
            } else {
                nsd.resolveService(found, listener)
            }
        } catch (e: RuntimeException) {
            synchronized(lock) { resolveListeners.remove(name) }
        }
    }

    private fun stopResolving(nsd: NsdManager, name: String) {
        val callback = synchronized(lock) { infoCallbacks.remove(name) }
        callback?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
    }

    override fun stopDiscovery() {
        val nsd = manager ?: return
        val taken = synchronized(lock) {
            Triple(discovery, infoCallbacks.values.toList(), resolveListeners.values.toList()).also {
                discovery = null
                infoCallbacks.clear()
                resolveListeners.clear()
            }
        }
        taken.first?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        taken.second.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        @Suppress("DEPRECATION")
        taken.third.forEach { runCatching { nsd.stopServiceResolution(it) } }
    }

    private fun copy(attributes: Map<String, ByteArray>): Map<String, ByteArray?> =
        attributes.mapValues { (_, value) -> value.copyOf() }
}
