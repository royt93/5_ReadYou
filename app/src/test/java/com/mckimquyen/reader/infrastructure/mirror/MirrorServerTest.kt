package com.mckimquyen.reader.infrastructure.mirror

import com.mckimquyen.reader.domain.mirror.MirrorFailure
import com.mckimquyen.reader.domain.mirror.MirrorLimits
import com.mckimquyen.reader.domain.mirror.MirrorSnapshot
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

class MirrorServerTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private var now = 1_000L
    private val server = MirrorServer(clock = { now })
    private val snapshot = MirrorSnapshot("Hello", "BBC", 5L, listOf("p1", "p2"), false, 1L)

    @After fun tearDown() = server.stop()

    private fun startOk(): MirrorStartResult.Started =
        server.start(loopback) as MirrorStartResult.Started

    private data class Reply(val status: Int, val headers: Map<String, String>, val body: String)

    private fun get(port: Int, path: String, method: String = "GET"): Reply =
        Socket(loopback, port).use { s ->
            s.soTimeout = 5_000
            s.getOutputStream().write("$method $path HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            val raw = s.getInputStream().readBytes().toString(Charsets.UTF_8)
            val head = raw.substringBefore("\r\n\r\n")
            val lines = head.split("\r\n")
            Reply(
                lines[0].split(" ")[1].toInt(),
                lines.drop(1).associate { it.substringBefore(": ").lowercase() to it.substringAfter(": ") },
                raw.substringAfter("\r\n\r\n", ""),
            )
        }

    @Test fun startBindsTheGivenAddressOnARandomPortAndReturnsAToken() {
        val started = startOk()
        assertTrue(started.port in 1..65535)
        assertEquals(32, started.token.value.length)
        assertTrue(server.isRunning)
    }

    @Test fun servesThePageAndStateForTheTokenAndNothingElse() {
        val started = startOk()
        server.publish(snapshot)
        val page = get(started.port, "/m/${started.token.value}")
        assertEquals(200, page.status)
        assertTrue(page.headers.getValue("content-type").startsWith("text/html"))
        assertTrue(page.body.contains("EventSource"))
        val state = get(started.port, "/m/${started.token.value}/state")
        assertEquals(200, state.status)
        assertTrue(state.headers.getValue("content-type").startsWith("application/json"))
        assertTrue(state.body.contains("\"title\":\"Hello\""))
        assertEquals(404, get(started.port, "/").status)
        assertEquals(404, get(started.port, "/m/" + "0".repeat(32)).status)
    }

    @Test fun securityHeadersAreSentOnEveryReply() {
        val started = startOk()
        listOf("/m/${started.token.value}", "/m/${started.token.value}/state", "/nope").forEach {
            val h = get(started.port, it).headers
            assertEquals(it, "no-store", h["cache-control"])
            assertEquals(it, "nosniff", h["x-content-type-options"])
            assertEquals(it, "no-referrer", h["referrer-policy"])
            assertEquals(it, "close", h["connection"])
        }
        val csp = get(started.port, "/m/${started.token.value}").headers["content-security-policy"]
        assertNotNull(csp)
        assertTrue(csp!!.contains("default-src 'none'"))
    }

    @Test fun nonGetAndBrokenRequestsAreRejectedWithoutKillingTheServer() {
        val started = startOk()
        assertEquals(405, get(started.port, "/m/${started.token.value}", method = "POST").status)
        Socket(loopback, started.port).use { it.getOutputStream().write("garbage\r\n\r\n".toByteArray()) }
        assertEquals(200, get(started.port, "/m/${started.token.value}/state").status)
    }

    @Test fun anOversizedRequestHeadGets431() {
        val started = startOk()
        val reply = Socket(loopback, started.port).use { s ->
            s.soTimeout = 5_000
            s.getOutputStream().write(("GET / HTTP/1.1\r\nX: " + "a".repeat(MirrorLimits.MAX_REQUEST_BYTES * 2) + "\r\n\r\n").toByteArray())
            s.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
        assertTrue(reply.startsWith("HTTP/1.1 431"))
    }

    @Test fun requestsOverTheMinuteLimitGet429AndAnotherMinuteResetsIt() {
        val started = startOk()
        repeat(MirrorLimits.MAX_REQUESTS_PER_MINUTE) { assertEquals(404, get(started.port, "/x").status) }
        val limited = get(started.port, "/x")
        assertEquals(429, limited.status)
        assertEquals("60", limited.headers["retry-after"])
        now += MirrorLimits.RATE_WINDOW_MILLIS
        assertEquals(404, get(started.port, "/x").status)
    }

    @Test fun eventsStreamSendsTheCurrentArticleThenEachNewOne() {
        val started = startOk()
        server.publish(snapshot)
        Socket(loopback, started.port).use { s ->
            s.soTimeout = 5_000
            s.getOutputStream().write("GET /m/${started.token.value}/events HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            val reader = s.getInputStream().bufferedReader()
            val lines = mutableListOf<String>()
            fun readUntil(text: String) { while (lines.none { it.contains(text) }) lines += reader.readLine() ?: error("closed early: $lines") }
            readUntil("\"title\":\"Hello\"")
            assertTrue(lines.any { it.startsWith("HTTP/1.1 200") })
            assertTrue(lines.any { it.lowercase().startsWith("content-type: text/event-stream") })
            assertTrue(lines.contains("event: article"))
            server.publish(snapshot.copy(title = "Second", version = 2))
            readUntil("\"title\":\"Second\"")
        }
    }

    @Test fun aFourthStreamIsRefusedWith503() {
        val started = startOk()
        val open = (1..MirrorLimits.MAX_CLIENTS).map {
            Socket(loopback, started.port).also { s ->
                s.getOutputStream().write("GET /m/${started.token.value}/events HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                s.soTimeout = 5_000
                s.getInputStream().bufferedReader().readLine()
            }
        }
        try {
            waitUntil { server.clients.value == MirrorLimits.MAX_CLIENTS }
            // a refused stream must not be rate-limited out of the test by the three above, so spread over IPs is not possible on loopback:
            now += MirrorLimits.RATE_WINDOW_MILLIS
            assertEquals(503, get(started.port, "/m/${started.token.value}/events").status)
        } finally { open.forEach { it.close() } }
    }

    @Test fun aSlowClientDoesNotBlockOthers() {
        val started = startOk()
        val stalled = Socket(loopback, started.port)
        try {
            stalled.getOutputStream().write("GET /m/".toByteArray()) // never finishes its request
            val t0 = System.nanoTime()
            assertEquals(200, get(started.port, "/m/${started.token.value}/state").status)
            assertTrue("took too long", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0) < 3_000)
        } finally { stalled.close() }
    }

    @Test fun stopClosesStreamsAndRefusesNewConnections() {
        val started = startOk()
        val stream = Socket(loopback, started.port)
        stream.getOutputStream().write("GET /m/${started.token.value}/events HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
        stream.soTimeout = 5_000
        stream.getInputStream().bufferedReader().readLine()
        server.stop()
        assertFalse(server.isRunning)
        assertEquals(0, server.clients.value)
        val end = runCatching { stream.getInputStream().readBytes(); true }.getOrDefault(true)
        assertTrue(end)
        assertTrue(runCatching { Socket(loopback, started.port).close() }.isFailure)
        stream.close()
    }

    @Test fun stopIsIdempotentAndSafeBeforeStart() {
        server.stop()
        startOk()
        server.stop()
        server.stop()
        assertFalse(server.isRunning)
    }

    @Test fun startingTwiceKeepsTheFirstSessionAndReportsAlreadyRunning() {
        val first = startOk()
        val second = server.start(loopback)
        assertEquals(MirrorStartResult.Failed(MirrorFailure.ALREADY_RUNNING), second)
        assertEquals(200, get(first.port, "/m/${first.token.value}/state").status)
    }

    @Test fun canRestartAfterStopWithANewTokenAndTheOldOneStopsWorking() {
        val first = startOk()
        server.stop()
        val second = startOk()
        assertTrue(first.token.value != second.token.value)
        assertEquals(404, get(second.port, "/m/${first.token.value}/state").status)
    }

    @Test fun bindFailureIsATypedErrorNotACrash() {
        val taken = ServerSocket(0, 1, loopback)
        taken.use {
            // 203.0.113.1 (TEST-NET-3) is not an address of this machine, so bind must fail.
            val result = server.start(InetAddress.getByName("203.0.113.1"))
            assertEquals(MirrorStartResult.Failed(MirrorFailure.BIND), result)
            assertFalse(server.isRunning)
        }
    }

    @Test fun theSocketIsBoundToTheGivenAddressNotToTheWildcard() {
        val started = startOk()
        assertEquals(loopback, server.boundAddress)
        assertFalse(server.boundAddress!!.isAnyLocalAddress)
        assertTrue(started.port > 0)
    }

    @Test fun anExpiredTokenStopsServingContent() {
        val started = startOk()
        now = started.token.expiresAtMillis
        assertEquals(404, get(started.port, "/m/${started.token.value}/state").status)
    }


    @Test fun aClosedStreamFreesItsSlotSoAFourthBrowserCanJoinLater() {
        val started = startOk()
        fun openStream() = Socket(loopback, started.port).also { s ->
            s.getOutputStream().write("GET /m/${started.token.value}/events HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            s.soTimeout = 5_000
            s.getInputStream().bufferedReader().readLine()
        }
        val first = (1..MirrorLimits.MAX_CLIENTS).map { openStream() }
        waitUntil { server.clients.value == MirrorLimits.MAX_CLIENTS }
        first.forEach { it.close() }
        // Closing the sockets alone is not enough: the server notices on its next write, which a publish triggers.
        repeat(3) { server.publish(snapshot.copy(version = 10L + it)); Thread.sleep(50) }
        waitUntil { server.clients.value == 0 }
        now += MirrorLimits.RATE_WINDOW_MILLIS
        val again = (1..MirrorLimits.MAX_CLIENTS).map { openStream() }
        try {
            waitUntil { server.clients.value == MirrorLimits.MAX_CLIENTS }
        } finally { again.forEach { it.close() } }
    }

    @Test fun anIdleConnectionIsClosedByTheServerAfterTheReadTimeout() {
        val fast = MirrorServer(clock = { now }, socketTimeoutMillis = 300)
        try {
            val started = fast.start(loopback) as MirrorStartResult.Started
            Socket(loopback, started.port).use { s ->
                s.soTimeout = 3_000
                s.getOutputStream().write("GET /m/".toByteArray()) // never completes the request
                val end = s.getInputStream().read()
                assertEquals("server must close an idle connection (EOF), not wait forever", -1, end)
            }
        } finally { fast.stop() }
    }

    @Test fun stopEndsAnOpenStreamForTheBrowserWithAnEndOfFile() {
        val started = startOk()
        Socket(loopback, started.port).use { stream ->
            stream.getOutputStream().write("GET /m/${started.token.value}/events HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            stream.soTimeout = 5_000
            val reader = stream.getInputStream().bufferedReader()
            while (reader.readLine().let { it != null && it.isNotEmpty() }) Unit // response head
            server.stop()
            var reachedEnd = false
            try {
                while (reader.readLine() != null) Unit
                reachedEnd = true
            } catch (e: java.net.SocketTimeoutException) {
                reachedEnd = false
            } catch (e: java.net.SocketException) {
                reachedEnd = true
            }
            assertTrue("an open event stream must end when the server stops", reachedEnd)
        }
    }

    private fun waitUntil(timeoutMs: Long = 5_000, block: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!block()) { check(System.nanoTime() < end) { "condition not met" }; Thread.sleep(20) }
    }
}
