package com.mckimquyen.reader.infrastructure.social

import android.app.Application
import com.mckimquyen.reader.infrastructure.net.SelfSignedTrust
import com.mckimquyen.reader.infrastructure.net.hostTrustingClientFrom
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyOutcome
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import com.mckimquyen.reader.infrastructure.social.nostr.NostrTestEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bluesky and the Nostr relays use the shared client, so they refuse a server whose certificate does not verify unless the user allowed that host and port. The proof
 * is a real TLS server with a certificate for another name, reached through the same hostTrustingClientFrom that the app builds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SocialClientsCertificateTest {
    private class MemoryStore : SelfSignedTrust.Store {
        var hosts: Set<String> = emptySet()
        override fun read() = hosts
        override fun write(hosts: Set<String>) { this.hosts = hosts }
    }

    private lateinit var server: MockWebServer
    private lateinit var base: String
    private val trust = SelfSignedTrust(MemoryStore())
    private val http = hostTrustingClientFrom(OkHttpClient(), trust)

    @Before fun start() {
        val host = java.net.InetAddress.getByName("localhost").hostName
        val cert = HeldCertificate.Builder().commonName("other.example").addSubjectAlternativeName("other.example").build()
        server = MockWebServer().apply {
            useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory())
            start()
        }
        base = server.url("/").toString().replace(server.hostName, host).trimEnd('/')
    }

    @After fun stop() {
        http.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    private fun bluesky() = BlueskyApiClient(http, Dispatchers.IO, base, 5_000L, 64 * 1024L)

    private fun enqueueBluesky() {
        val profile = """{"did":"${BlueskyTestPayloads.DID}","handle":"${BlueskyTestPayloads.HANDLE}","displayName":"A"}"""
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(profile))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"feed":[]}"""))
    }

    @Test fun blueskyIsRefusedForAnUnknownCertificate_andWorksOnceTheHostIsAllowed() = runBlocking {
        enqueueBluesky()
        assertEquals(BlueskyOutcome.Failed, bluesky().fetch(BlueskyTestPayloads.HANDLE))
        assertEquals("no request may reach the server over an unverified certificate", 0, server.requestCount)
        trust.setTrusted(base, true)
        assertTrue(bluesky().fetch(BlueskyTestPayloads.HANDLE) is BlueskyOutcome.Found)
    }

    private fun relay() = MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text.startsWith("[\"REQ\"")) {
                webSocket.send("[\"EVENT\",\"f2\",${NostrTestEvents.PLAIN}]")
                webSocket.send("[\"EOSE\",\"f2\"]")
            }
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = webSocket.close(code, reason).let { }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = Unit
    })

    @Test fun aNostrRelayIsRefusedForAnUnknownCertificate_andReadOnceTheHostIsAllowed() = runBlocking {
        val url = base.replaceFirst("https://", "wss://")
        val client = NostrRelayClient(http, Dispatchers.IO, listOf(url), true, 5_000L)
        server.enqueue(relay())
        assertTrue("refused relay gives nothing", client.fetch(NostrTestEvents.AUTHOR).notes.isEmpty())
        assertEquals(0, server.requestCount)
        trust.setTrusted(base, true)
        server.enqueue(relay())
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), client.fetch(NostrTestEvents.AUTHOR).notes.map { it.id })
    }
}
