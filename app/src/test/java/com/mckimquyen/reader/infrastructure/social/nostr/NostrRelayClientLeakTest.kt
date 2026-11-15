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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Many fetches in a row must not leave sockets, threads or the client itself behind: a leak here would grow with every
 * background sync of a Nostr source.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrRelayClientLeakTest {
    private lateinit var server: MockWebServer
    private val opened = AtomicInteger()
    private val closedByServer = AtomicInteger()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() = server.shutdown()

    private fun relay(vararg events: String, sendEose: Boolean = true): MockResponse =
        MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { opened.incrementAndGet() }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!text.startsWith("[\"REQ\"")) return
                events.forEach { webSocket.send("[\"EVENT\",\"f2\",$it]") }
                if (sendEose) webSocket.send("[\"EOSE\",\"f2\"]")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                closedByServer.incrementAndGet()
                webSocket.close(code, reason)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closedByServer.incrementAndGet() }
        })

    private fun url() = server.url("/").toString()

    private fun waitFor(seconds: Long, condition: () -> Boolean): Boolean {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (System.nanoTime() < end) { if (condition()) return true; Thread.sleep(POLL_MILLIS) }
        return condition()
    }

    /** Runs the fetches inside its own frame so no strong reference to the client survives on this test's stack. */
    private fun fetchManyAndReturnWeakReferences(http: OkHttpClient): List<WeakReference<NostrRelayClient>> {
        val client = NostrRelayClient(http, Dispatchers.IO, listOf(url()), requireSecure = false, timeoutMs = 3_000L)
        repeat(FETCHES) { server.enqueue(relay(NostrTestEvents.PLAIN)) }
        repeat(FETCHES) {
            val notes = runBlocking { client.fetch(NostrTestEvents.AUTHOR).notes }
            assertEquals(1, notes.size)
        }
        return listOf(WeakReference(client))
    }

    @Test fun manyFetches_leaveNoSocketOrClientBehind() {
        val http = OkHttpClient()
        val weak = fetchManyAndReturnWeakReferences(http)
        assertTrue("the server saw every socket close (opened=${opened.get()}, closed=${closedByServer.get()})", waitFor(5) { closedByServer.get() >= FETCHES })
        assertEquals("every fetch opened exactly one socket", FETCHES, opened.get())
        assertTrue("no open connection stays in the pool", waitFor(5) { http.connectionPool.connectionCount() == 0 })
        http.dispatcher.executorService.shutdown()
        // OkHttp's TaskRunner is one process-wide pool of daemon threads that idle for 60 s, so the thread count never
        // returns to zero within a test; what matters is that it does not grow with the number of fetches (next test).
        assertTrue("the client is garbage collected once nobody uses it", waitFor(10) { System.gc(); weak[0].get() == null })
    }

    @Test fun threadCount_doesNotGrowWithTheNumberOfFetches() {
        val http = OkHttpClient()
        val client = NostrRelayClient(http, Dispatchers.IO, listOf(url()), requireSecure = false, timeoutMs = 3_000L)
        repeat(WARMUP_FETCHES + FETCHES) { server.enqueue(relay(NostrTestEvents.PLAIN)) }
        repeat(WARMUP_FETCHES) { runBlocking { client.fetch(NostrTestEvents.AUTHOR).notes } }
        waitFor(3) { closedByServer.get() >= WARMUP_FETCHES }
        val afterWarmup = Thread.getAllStackTraces().keys.count { it.isAlive }
        repeat(FETCHES) { runBlocking { client.fetch(NostrTestEvents.AUTHOR).notes } }
        waitFor(5) { closedByServer.get() >= WARMUP_FETCHES + FETCHES }
        val afterMany = Thread.getAllStackTraces().keys.count { it.isAlive }
        assertTrue("threads grew from $afterWarmup to $afterMany over $FETCHES fetches", afterMany - afterWarmup <= THREAD_SLACK)
        http.dispatcher.executorService.shutdown()
    }

    @Test fun fetchesThatTimeOut_alsoReleaseTheirSocket() {
        val http = OkHttpClient()
        val client = NostrRelayClient(http, Dispatchers.IO, listOf(url()), requireSecure = false, timeoutMs = 200L)
        repeat(TIMEOUT_FETCHES) { server.enqueue(relay(sendEose = false)) }
        repeat(TIMEOUT_FETCHES) { assertTrue(runBlocking { client.fetch(NostrTestEvents.AUTHOR).notes }.isEmpty()) }
        assertTrue("every timed-out socket was dropped (opened=${opened.get()}, closed=${closedByServer.get()})", waitFor(5) { closedByServer.get() >= TIMEOUT_FETCHES })
        assertTrue("no open connection stays in the pool", waitFor(5) { http.connectionPool.connectionCount() == 0 })
        http.dispatcher.executorService.shutdown()
    }

    private companion object {
        const val FETCHES = 30
        const val WARMUP_FETCHES = 3
        const val THREAD_SLACK = 4
        const val TIMEOUT_FETCHES = 10
        const val POLL_MILLIS = 20L
    }
}
