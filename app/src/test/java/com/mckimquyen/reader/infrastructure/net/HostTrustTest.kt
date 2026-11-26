package com.mckimquyen.reader.infrastructure.net

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import javax.net.ssl.SSLException

/** The shared client verifies certificates; only the hosts the user allowed get through, and switching one on or off applies to the next connection. */
class HostTrustTest {
    private class MemoryStore : SelfSignedTrust.Store {
        var hosts: Set<String> = emptySet()
        override fun read() = hosts
        override fun write(hosts: Set<String>) { this.hosts = hosts }
    }

    private lateinit var server: MockWebServer
    private lateinit var url: String
    private val store = MemoryStore()
    private val trust = SelfSignedTrust(store)
    private val client = hostTrustingClientFrom(OkHttpClient(), trust)

    @Before
    fun start() {
        val host = java.net.InetAddress.getByName("localhost").hostName
        // The certificate names a different host than the one we connect to, so both the chain check and the host name check have to be relaxed for the allowed host.
        val cert = HeldCertificate.Builder().commonName("other.example").addSubjectAlternativeName("other.example").build()
        server = MockWebServer().apply {
            useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory())
            repeat(6) { enqueue(MockResponse().setBody("ok")) }
            start()
        }
        url = server.url("/").toString().replace(server.hostName, host)
    }

    @After fun stop() = server.shutdown()

    private fun get(c: OkHttpClient = client) = c.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }

    private fun tlsFailure(e: Exception) = (listOf(e, e.cause) + e.suppressed.toList()).any { it is SSLException }

    @Test fun anUnknownCertificateIsRefusedByDefault() {
        val e = assertThrows(Exception::class.java) { get() }
        assertTrue("expected a TLS failure: $e ${e.suppressed.toList()}", tlsFailure(e))
    }

    @Test fun anAllowedHostIsAcceptedWithoutRebuildingTheClient() {
        trust.setTrusted(url, true)
        assertEquals("ok", get())
        trust.setTrusted(url, false)
        assertTrue(tlsFailure(assertThrows(Exception::class.java) { get() }))
    }

    @Test fun anotherPortOfTheSameHostStaysRefused() {
        val other = "https://localhost:${server.port + 1}/"
        trust.setTrusted(other, true)
        assertTrue(tlsFailure(assertThrows(Exception::class.java) { get() }))
    }

    @Test fun trustingOneHostDoesNotTrustAnother() {
        trust.setTrusted("https://somewhere.else/", true)
        assertTrue(tlsFailure(assertThrows(Exception::class.java) { get() }))
    }

    @Test fun theKeyOfAUrlAndOfALiveSocketAgree() {
        assertEquals("example.com", SelfSignedTrust.keyOf("https://Example.com:443/feed"))
        assertEquals("example.com", HostKey.of("example.com", 443))
        assertEquals("example.com:8443", HostKey.of("EXAMPLE.com", 8443))
    }

    @Test fun theSecureClientStaysStrictEvenForAHostTheUserAllowed() {
        trust.setTrusted(url, true)
        val secure = com.mckimquyen.reader.infrastructure.di.secureHttpClientFrom(client)
        assertEquals("ok", get(client))
        assertTrue("the Gemini client must not inherit the host exception", tlsFailure(assertThrows(Exception::class.java) { get(secure) }))
    }

    @Test fun theSharedClientIsBuiltWithoutTrustAll() {
        val src = listOf(java.io.File("src/main/java"), java.io.File("app/src/main/java")).first { it.exists() }
            .resolve("com/mckimquyen/reader/infrastructure/di/OkHttpClientModule.kt").readText()
        val provider = src.substringAfter("fun provideOkHttpClient(").substringBefore("fun provideSecureHttpClient")
        assertTrue("the shared client must pass trustAllCerts = false", "trustAllCerts = false" in provider)
        assertTrue("and go through hostTrustingClientFrom", "hostTrustingClientFrom(" in provider)
    }

    // Closing a pooled TLS socket writes to the network; on the main thread Android throws NetworkOnMainThreadException (seen on a Tecno when the switch was tapped).
    @Test fun theEvictionRunsOffTheCallingThreadAndIsDoneWhenTheCallReturns() {
        val caller = Thread.currentThread()
        var ranOn: Thread? = null
        var done = false
        runOffCallingThread { ranOn = Thread.currentThread(); Thread.sleep(50); done = true }
        assertTrue("must not run on the caller", ranOn != null && ranOn !== caller)
        assertTrue("the next request must not reuse a connection from the old rule", done)
    }

    @Test fun theChangeHookEvictsThroughTheOffThreadHelper() {
        val src = listOf(java.io.File("src/main/java"), java.io.File("app/src/main/java")).first { it.exists() }
            .resolve("com/mckimquyen/reader/infrastructure/net/HostTrust.kt").readText()
        val hook = src.substringAfter("trust.onChanged =").substringBefore("\n")
        assertTrue("evictAll must go through runOffCallingThread: $hook", "runOffCallingThread" in hook && "evictAll" in hook)
    }
}
