package com.mckimquyen.reader.infrastructure.di

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import javax.net.ssl.SSLException

/**
 * A real HTTPS server with a self-signed certificate. Fever, Google Reader and WebDAV send a password, so by default they must REFUSE it; only a host the user switched
 * the exception on for may use it. This runs the actual TLS handshake, it does not inspect source text.
 */
class CredentialHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var url: String

    @Before fun start() {
        val host = java.net.InetAddress.getByName("localhost").hostName
        val cert = HeldCertificate.Builder().commonName(host).addSubjectAlternativeName(host).build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        server = MockWebServer().apply {
            useHttps(serverTls.sslSocketFactory())
            enqueue(MockResponse().setBody("ok"))
            enqueue(MockResponse().setBody("ok"))
            start()
        }
        // The URL keeps the host name the server really listens on, so a refusal can only come from the certificate, not from a failed connect.
        url = server.url("/").toString()
    }

    @After fun stop() = server.shutdown()

    private fun get(client: OkHttpClient) = client.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }

    @Test fun theDefaultClientRefusesASelfSignedCertificate() {
        val strict = credentialHttpClientFrom(OkHttpClient(), trustSelfSigned = false)
        val e = assertThrows(Exception::class.java) { get(strict) }
        // "localhost" resolves to ::1 and 127.0.0.1; OkHttp reports the refused ::1 route first and keeps the TLS failure of the 127.0.0.1 route as suppressed.
        val tlsFailure = (listOf(e, e.cause) + e.suppressed.toList()).any { it is SSLException }
        assertEquals("must be a TLS failure, not only a connection failure: $e ${e.suppressed.toList()}", true, tlsFailure)
    }

    @Test fun theSharedTrustAllClientWouldHaveAcceptedIt_whichIsWhyItMustNotCarryPasswords() {
        val trustAll = cachingHttpClient()
        assertEquals("ok", get(trustAll))
    }

    @Test fun aHostTheUserAllowedIsAccepted() {
        val allowed = credentialHttpClientFrom(OkHttpClient(), trustSelfSigned = true)
        assertEquals("ok", get(allowed))
    }

    @Test fun theStrictClientKeepsTheTimeoutsOfItsBase() {
        val base = OkHttpClient.Builder().readTimeout(41, java.util.concurrent.TimeUnit.SECONDS).build()
        assertEquals(41_000, credentialHttpClientFrom(base, false).readTimeoutMillis)
        assertEquals(41_000, credentialHttpClientFrom(base, true).readTimeoutMillis)
    }
}
