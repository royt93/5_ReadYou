package com.mckimquyen.reader.infrastructure.rss.scraper

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WebFeedHttpClientTest {
    @Test
    fun mockWebServerRedirectIsReturnedWithoutFollowingLocation() {
        val server = httpsServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/next"))
            val client = testClient()

            val response = client.newCall(Request.Builder().url(server.url("/start")).build()).execute()

            assertEquals(302, response.code)
            assertEquals(1, server.requestCount)
            response.close()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun mockWebServerHttpErrorIsReturnedWithoutRetry() {
        val server = httpsServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

            val response = testClient().newCall(Request.Builder().url(server.url("/error")).build()).execute()

            assertEquals(503, response.code)
            assertEquals(1, server.requestCount)
            response.close()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun mockWebServerCallTimeoutIsEnforced() {
        val server = httpsServer()
        server.start()
        try {
            server.enqueue(MockResponse().setHeadersDelay(2, TimeUnit.SECONDS))
            val client = testClient().newBuilder().callTimeout(100, TimeUnit.MILLISECONDS).build()

            assertThrows(Exception::class.java) {
                client.newCall(Request.Builder().url(server.url("/slow")).build()).execute()
            }
        } finally {
            server.shutdown()
        }
    }

    private fun testClient() = WebFeedHttpClient.newBuilder()
        .dns(Dns { listOf(InetAddress.getByName("127.0.0.1")) })
        .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    private fun httpsServer(): MockWebServer = MockWebServer().apply {
        useHttps(serverCertificates.sslSocketFactory())
    }

    private companion object {
        val serverCertificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(serverCertificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(serverCertificate.certificate)
            .build()
    }

    @Test
    fun clientDisablesRedirectsAndPinsValidatedDnsAnswers() {
        val publicAddress = InetAddress.getByName("93.184.216.34")
        val client = WebFeedHttpClient.create(
            "https://news.example/rss",
            Dns { host ->
                assertEquals("news.example", host)
                listOf(publicAddress)
            },
        )

        assertNotNull(client)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertEquals(listOf(publicAddress), client.dns.lookup("news.example"))
        assertThrows(IllegalArgumentException::class.java) { client.dns.lookup("other.example") }
    }

    @Test
    fun clientRejectsUnsafeDnsAnswersBeforeReturningHttpClient() {
        assertThrows(IllegalArgumentException::class.java) {
            WebFeedHttpClient.create("https://news.example/rss", Dns { listOf(InetAddress.getByName("192.168.1.10")) })
        }
    }

    @Test
    fun clientRejectsCleartextUrlBeforeDnsLookup() {
        var dnsCalled = false
        assertThrows(IllegalArgumentException::class.java) {
            WebFeedHttpClient.create("http://news.example/rss", Dns {
                dnsCalled = true
                listOf(InetAddress.getByName("93.184.216.34"))
            })
        }
        assertFalse(dnsCalled)
    }
}
