package com.mckimquyen.reader.infrastructure.rss.provider

import com.mckimquyen.reader.infrastructure.rss.provider.fever.FeverAPI
import com.mckimquyen.reader.infrastructure.rss.provider.googleReader.GoogleReaderApi
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.net.ssl.SSLException

/** Fever and Google Reader send a password: the default client must refuse a self-signed certificate, and only a host the user allowed may be accepted. */
class CredentialApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var url: String

    @Before
    fun start() {
        val host = java.net.InetAddress.getByName("localhost").hostName
        val cert = HeldCertificate.Builder().commonName(host).addSubjectAlternativeName(host).build()
        server = MockWebServer().apply {
            useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory())
            enqueue(MockResponse().setBody("ok"))
            enqueue(MockResponse().setBody("ok"))
            start()
        }
        url = server.url("/").toString()
    }

    @After fun stop() = server.shutdown()

    private fun get(client: okhttp3.OkHttpClient) = client.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }

    @Test
    fun theDefaultCredentialClientRefusesASelfSignedCertificate() {
        val e = assertThrows(Exception::class.java) { get(credentialApiClient(trustSelfSigned = false)) }
        // localhost resolves to ::1 first; the TLS failure of the 127.0.0.1 route is kept as suppressed.
        assertTrue("expected a TLS failure: $e ${e.suppressed.toList()}", (listOf(e, e.cause) + e.suppressed.toList()).any { it is SSLException })
    }

    @Test
    fun theAllowedCredentialClientAcceptsIt() {
        assertEquals("ok", get(credentialApiClient(trustSelfSigned = true)))
    }

    @Test
    fun feverInstancesAreKeptApartByTheTrustFlag() {
        val strict = FeverAPI.getInstance("https://fever.test/api", "u", "p")
        val allowed = FeverAPI.getInstance("https://fever.test/api", "u", "p", trustSelfSigned = true)
        assertNotSame(strict, allowed)
        assertSame(strict, FeverAPI.getInstance("https://fever.test/api", "u", "p"))
    }

    @Test
    fun googleReaderInstancesAreKeptApartByTheTrustFlag() {
        val strict = GoogleReaderApi.getInstance("https://gr.test", "u", "p")
        val allowed = GoogleReaderApi.getInstance("https://gr.test", "u", "p", trustSelfSigned = true)
        assertNotSame(strict, allowed)
    }
}
