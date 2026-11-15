package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrRelayProfileTest {
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private val sent = CopyOnWriteArrayList<String>()

    @Before fun setUp() { server = MockWebServer().apply { start() }; http = OkHttpClient() }
    @After fun tearDown() { http.dispatcher.executorService.shutdownNow(); server.shutdown() }
    private fun url() = server.url("/").toString()
    private fun client(vararg urls: String) = NostrRelayClient(http, Dispatchers.IO, urls.toList(), false, 1_000L)

    private fun relay(vararg events: String) = MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            sent += text
            if (!text.startsWith("[\"REQ\"")) return
            events.forEach { webSocket.send("[\"EVENT\",\"f2\",$it]") }
            webSocket.send("[\"EOSE\",\"f2\"]")
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = webSocket.close(code, reason).let { }
    })

    @Test fun oneReq_hasBothExactFilters_underOneSubscription() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PLAIN, NostrTestEvents.PROFILE))
        val result = client(url()).fetch(NostrTestEvents.AUTHOR)
        assertEquals(1, result.notes.size)
        assertEquals("Alice Liddell", result.profileName)
        val req = sent.single { it.startsWith("[\"REQ\"") }
        assertTrue(req.startsWith("[\"REQ\",\"f2\","))
        assertTrue(req.contains("{\"kinds\":[1],\"authors\":[\"${NostrTestEvents.AUTHOR}\"],\"limit\":50}"))
        assertTrue(req.contains("{\"kinds\":[0],\"authors\":[\"${NostrTestEvents.AUTHOR}\"],\"limit\":1}"))
    }

    @Test fun kind0_neverAppearsInNotes() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE, NostrTestEvents.PLAIN))
        val result = client(url()).fetch(NostrTestEvents.AUTHOR)
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), result.notes.map { it.id })
    }

    @Test fun profileName_prefersDisplayName_throughProfileParser() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE))
        assertEquals("Alice Liddell", client(url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }

    @Test fun wrongAuthors_andBadSignatures_cannotSupplyTheProfileName() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE_STRANGER))
        server.enqueue(MockResponse().setResponseCode(503))
        val result = client(url(), url()).fetch(NostrTestEvents.AUTHOR)
        assertTrue(result.notes.isEmpty())
        assertNull(result.profileName)
    }

    @Test fun theNewestValidProfileWins_evenIfTheRelaySendsAnOlderProfileLater() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE_NEWER, NostrTestEvents.PROFILE_OLDER, NostrTestEvents.PROFILE))
        assertEquals("alice2", client(url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }

    // The newest profile arrives LAST: only sorting by created_at (not arrival order) picks it.
    @Test fun theNewestValidProfileWins_evenWhenItArrivesAfterOlderOnes() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE_OLDER, NostrTestEvents.PROFILE, NostrTestEvents.PROFILE_NEWER))
        assertEquals("alice2", client(url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }

    @Test fun aNewestProfileWithoutAUsableName_fallsBackToAnOlderProfileThatHasOne() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE_BLANK_NEWER, NostrTestEvents.PROFILE))
        assertEquals("Alice Liddell", client(url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }

    @Test fun noProfileName_keepsTheFallbackNull() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PLAIN))
        assertNull(client(url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }

    @Test fun noNotesButAProfile_isReturnedForTheCallerToRejectAsAnEmptyFeed() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE))
        val result = client(url()).fetch(NostrTestEvents.AUTHOR)
        assertTrue(result.notes.isEmpty())
        assertEquals("Alice Liddell", result.profileName)
    }

    @Test fun subIdMismatch_cannotSupplyAProfile() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!text.startsWith("[\"REQ\"")) return
                webSocket.send("[\"EVENT\",\"wrong\",${NostrTestEvents.PROFILE}]")
                webSocket.send("[\"EOSE\",\"f2\"]")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = webSocket.close(code, reason).let { }
        }))
        server.enqueue(MockResponse().setResponseCode(503))
        assertNull(client(url(), url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }

    @Test fun fallsBackToTheSecondRelay_whenFirstHasOnlyAProfile_andPrefersSecondName() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE_OLDER))
        server.enqueue(relay(NostrTestEvents.PLAIN, NostrTestEvents.PROFILE_NEWER))
        val result = client(url(), url()).fetch(NostrTestEvents.AUTHOR)
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), result.notes.map { it.id })
        assertEquals("alice2", result.profileName)
    }

    @Test fun fallsBackToTheFirstProfile_whenSecondHasNoName() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PROFILE))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        assertEquals("Alice Liddell", client(url(), url()).fetch(NostrTestEvents.AUTHOR).profileName)
    }
}
