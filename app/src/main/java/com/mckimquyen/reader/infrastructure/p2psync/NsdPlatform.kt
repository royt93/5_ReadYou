package com.mckimquyen.reader.infrastructure.p2psync

/** Thin boundary over NsdManager so discovery logic is testable without Android. */
interface NsdPlatform {
    fun register(serviceName: String, port: Int, attributes: Map<String, ByteArray>, onResult: (Boolean) -> Unit)
    fun unregister()
    fun discover(
        onFound: (serviceName: String, host: String?, port: Int, attributes: Map<String, ByteArray?>) -> Unit,
        onLost: (serviceName: String) -> Unit,
        onFailed: () -> Unit,
    )
    fun stopDiscovery()
}
