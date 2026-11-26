package com.mckimquyen.reader.infrastructure.net

import okhttp3.OkHttpClient
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * Trust manager that checks certificates like a browser, except for the servers the user has allowed (see [SelfSignedTrust]).
 *
 * The decision is taken at handshake time from the host and port the socket is really connected to, so one shared client serves every feed and an exception that is switched
 * on or off takes effect on the next connection without rebuilding any client.
 */
internal class HostTrustManager(
    private val system: X509TrustManager,
    private val trust: SelfSignedTrust,
) : X509ExtendedTrustManager() {

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) {
        try {
            if (system is X509ExtendedTrustManager) system.checkServerTrusted(chain, authType, socket) else system.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            if (!trusts(socket)) throw e
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) {
        try {
            if (system is X509ExtendedTrustManager) system.checkServerTrusted(chain, authType, engine) else system.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            if (engine == null || !trust.isTrusted(HostKey.of(engine.peerHost, engine.peerPort))) throw e
        }
    }

    /** No socket, so no host to compare with: never relaxed. */
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = system.checkServerTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket?) = system.checkClientTrusted(chain, authType)
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine?) = system.checkClientTrusted(chain, authType)
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = system.checkClientTrusted(chain, authType)
    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

    private fun trusts(socket: Socket?): Boolean {
        if (socket == null) return false
        val session = (socket as? SSLSocket)?.handshakeSession
        val host = session?.peerHost ?: socket.inetAddress?.hostName ?: return false
        val port = session?.peerPort?.takeIf { it > 0 } ?: socket.port
        return trust.isTrusted(HostKey.of(host, port))
    }
}

internal object HostKey {
    /** The same key [SelfSignedTrust.keyOf] gives for a URL: lower-case host, with the port only when it is not 443. */
    fun of(host: String?, port: Int): String? = SelfSignedTrust.keyFor(host, port)
}

/**
 * [base] with certificate and host name checks like a browser, plus a per-server exception for the hosts in [trust]. Replaces the old client that accepted every certificate.
 */
fun hostTrustingClientFrom(base: OkHttpClient, trust: SelfSignedTrust): OkHttpClient {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    val system = factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    val manager = HostTrustManager(system, trust)
    val ssl = SSLContext.getInstance("TLS")
    ssl.init(null, arrayOf<TrustManager>(manager), null)
    val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
    val built = base.newBuilder()
        .sslSocketFactory(ssl.socketFactory, manager)
        .hostnameVerifier { host, session ->
            defaultVerifier.verify(host, session) || trust.isTrusted(HostKey.of(host, session.peerPort))
        }
        .build()
    // Any change of an exception ends the pooled connections: they were handshaken under the previous rule.
    trust.onChanged = { runOffCallingThread { built.connectionPool.evictAll() } }
    return built
}

private const val EVICT_WAIT_MS = 2_000L

/**
 * Closing a pooled TLS socket writes the close_notify alert, and Android refuses network I/O on the main thread (NetworkOnMainThreadException crashed the feed health
 * switch on a real phone). The work runs on a short-lived thread and the caller waits for it, so the next request cannot reuse a connection from the old rule.
 */
internal fun runOffCallingThread(block: () -> Unit) {
    val worker = Thread { runCatching(block) }
    worker.start()
    worker.join(EVICT_WAIT_MS)
}
