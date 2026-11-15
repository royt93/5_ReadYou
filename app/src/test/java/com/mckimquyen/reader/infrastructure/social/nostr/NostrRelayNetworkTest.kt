package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketPolicy
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Bad-network behaviour of the relay client: refused, stalled, slow, cut mid-way, and a relay that answers late. Every case
 * must end in a bounded time with either the notes or an empty list, never a hang and never an exception.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrRelayNetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    }

    @After fun tearDown() {
        http.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    private fun url() = server.url("/").toString()
    private fun client(vararg urls: String, timeoutMs: Long = 600L) = NostrRelayClient(http, Dispatchers.IO, urls.toList(), requireSecure = false, timeoutMs = timeoutMs)

    private fun goodRelay(vararg events: String, beforeAnswerMillis: Long = 0L): MockResponse =
        MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!text.startsWith("[\"REQ\"")) return
                if (beforeAnswerMillis > 0) Thread.sleep(beforeAnswerMillis)
                events.forEach { webSocket.send("[\"EVENT\",\"f2\",$it]") }
                webSocket.send("[\"EOSE\",\"f2\"]")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
        })

    private fun timed(block: () -> List<NostrEvent>): Pair<List<NostrEvent>, Long> {
        val t0 = System.nanoTime()
        val result = block()
        return result to TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
    }

    @Test fun aRelayThatStallsTheConnection_isGivenUpOn_withinTheTimeout_andTheNextOneIsUsed() {
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.STALL_SOCKET_AT_START })
        server.enqueue(goodRelay(NostrTestEvents.PLAIN))
        val (notes, ms) = timed { runBlocking { client(url(), url()).fetch(NostrTestEvents.AUTHOR) } }
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), notes.map { it.id })
        assertTrue("took $ms ms for one stalled relay with a 600 ms timeout", ms < 5_000)
    }

    @Test fun aRelayThatDropsTheConnectionAtOnce_isSkipped() {
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AT_START })
        server.enqueue(goodRelay(NostrTestEvents.PLAIN))
        val (notes, ms) = timed { runBlocking { client(url(), url()).fetch(NostrTestEvents.AUTHOR) } }
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), notes.map { it.id })
        assertTrue("took $ms ms", ms < 5_000)
    }

    @Test fun anOpenSocketThatFailsAfterUpgrade_fallsBackWithoutWaitingForTheTimeout() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.startsWith("[\"REQ\"")) webSocket.close(1011, "relay error")
                }
            }),
        )
        server.enqueue(goodRelay(NostrTestEvents.PLAIN))
        val (notes, ms) = timed { runBlocking { client(url(), url(), timeoutMs = 4_000L).fetch(NostrTestEvents.AUTHOR) } }
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), notes.map { it.id })
        assertTrue("fallback after the socket failure must be fast; took $ms ms", ms < 3_000)
    }

    @Test fun aPeerClosingAnOpenSocket_fallsBackWithoutWaitingForTheTimeout() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.startsWith("[\"REQ\"")) webSocket.close(1001, "going away")
                }
            }),
        )
        server.enqueue(goodRelay(NostrTestEvents.PLAIN))
        val (notes, ms) = timed { runBlocking { client(url(), url(), timeoutMs = 4_000L).fetch(NostrTestEvents.AUTHOR) } }
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), notes.map { it.id })
        assertTrue("fallback after close must be fast; took $ms ms", ms < 3_000)
    }

    // With a long timeout, only the failure callback can make a dead connection fall back quickly.
    @Test fun aConnectionThatBreaks_fallsBackAtOnce_notAtTheEndOfAFourSecondTimeout() {
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.DISCONNECT_AT_START })
        server.enqueue(goodRelay(NostrTestEvents.PLAIN))
        val (notes, ms) = timed { runBlocking { client(url(), url(), timeoutMs = 4_000L).fetch(NostrTestEvents.AUTHOR) } }
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), notes.map { it.id })
        assertTrue("a broken connection must not cost the whole timeout; took $ms ms", ms < 3_000)
    }

    @Test fun aRelayThatCutsTheHandshake_isSkipped() {
        server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.FAIL_HANDSHAKE })
        server.enqueue(goodRelay(NostrTestEvents.PLAIN))
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), runBlocking { client(url(), url()).fetch(NostrTestEvents.AUTHOR) }.map { it.id })
    }

    @Test fun aRelayThatAnswersTooLate_costsExactlyTheTimeout_thenTheNextRelayIsUsed() {
        server.enqueue(goodRelay(NostrTestEvents.OLDER, beforeAnswerMillis = 3_000L))
        server.enqueue(goodRelay(NostrTestEvents.PLAIN))
        val (notes, ms) = timed { runBlocking { client(url(), url(), timeoutMs = 400L).fetch(NostrTestEvents.AUTHOR) } }
        assertEquals("only the fast second relay should be returned", listOf(NostrTestEvents.PLAIN_ID), notes.map { it.id })
        // It waited for two relays, so total time must exceed one 400 ms timeout. Cap loosely to reject hanging but avoid
        // a test that flakes due to a busy JVM/OS scheduler.
        assertTrue("took $ms ms", ms in 400..8_000)
    }

    @Test fun aSlowButInTimeRelay_stillDeliversItsNotes() {
        server.enqueue(goodRelay(NostrTestEvents.PLAIN, beforeAnswerMillis = 300L))
        val (notes, ms) = timed { runBlocking { client(url(), timeoutMs = 3_000L).fetch(NostrTestEvents.AUTHOR) } }
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), notes.map { it.id })
        assertTrue("took $ms ms", ms >= 300)
    }

    @Test fun aRelayThatClosesAfterOneNoteWithoutEose_keepsTheNoteItSent() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (!text.startsWith("[\"REQ\"")) return
                    webSocket.send("[\"EVENT\",\"f2\",${NostrTestEvents.PLAIN}]")
                    webSocket.close(1001, "going away")
                }
            }),
        )
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), runBlocking { client(url()).fetch(NostrTestEvents.AUTHOR) }.map { it.id })
    }

    @Test fun aRelayThatTimesOutAfterSendingSomeNotes_keepsWhatItSent() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.startsWith("[\"REQ\"")) webSocket.send("[\"EVENT\",\"f2\",${NostrTestEvents.PLAIN}]")
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            }),
        )
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), runBlocking { client(url(), timeoutMs = 500L).fetch(NostrTestEvents.AUTHOR) }.map { it.id })
    }

    @Test fun whenEveryRelayIsDown_theAnswerIsEmptyAndBounded() {
        val dead = MockWebServer().apply { start() }
        val deadUrl = dead.url("/").toString()
        dead.shutdown()
        val (notes, ms) = timed { runBlocking { client(deadUrl, deadUrl).fetch(NostrTestEvents.AUTHOR) } }
        assertTrue(notes.isEmpty())
        assertTrue("took $ms ms", ms < 5_000)
    }

    @Test fun aRelayThatNeverAnswersTheUpgrade_cannotHoldTheCallerLongerThanItsTimeoutPerRelay() {
        repeat(2) { server.enqueue(MockResponse().apply { socketPolicy = SocketPolicy.NO_RESPONSE }) }
        val (notes, ms) = timed { runBlocking { client(url(), url(), timeoutMs = 400L).fetch(NostrTestEvents.AUTHOR) } }
        assertTrue(notes.isEmpty())
        assertTrue("took $ms ms for two silent relays with a 400 ms timeout each", ms < 4_000)
    }
}
