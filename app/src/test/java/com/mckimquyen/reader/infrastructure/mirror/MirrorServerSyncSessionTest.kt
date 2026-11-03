package com.mckimquyen.reader.infrastructure.mirror

import com.mckimquyen.reader.domain.mirror.MirrorFailure
import com.mckimquyen.reader.domain.mirror.MirrorLimits
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import com.mckimquyen.reader.domain.p2psync.SyncLimits
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.Socket

class MirrorServerSyncSessionTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private var now = 1_000L
    private val server = MirrorServer(clock = { now })
    private val ciphertext = ByteArray(300) { (it * 7 - 100).toByte() }

    @After fun tearDown() = server.stop()

    private class Reply(val status: Int, val headers: Map<String, String>, val body: ByteArray)

    private fun startOk(bytes: ByteArray = ciphertext) = server.startSync(loopback, bytes) as MirrorStartResult.Started

    private fun get(port: Int, path: String, method: String = "GET"): Reply =
        Socket(loopback, port).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().write("$method $path HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            val raw = socket.getInputStream().readBytes()
            val split = String(raw, Charsets.ISO_8859_1).indexOf("\r\n\r\n")
            val lines = String(raw, 0, split, Charsets.ISO_8859_1).split("\r\n")
            Reply(
                lines[0].split(" ")[1].toInt(),
                lines.drop(1).associate { it.substringBefore(": ").lowercase() to it.substringAfter(": ") },
                raw.copyOfRange(split + 4, raw.size),
            )
        }

    @Test fun servesTheEnvelopeByteForByteAsOctetStream() {
        val started = startOk()
        val reply = get(started.port, "/s/${started.token.value}")
        assertEquals(200, reply.status)
        assertEquals("application/octet-stream", reply.headers.getValue("content-type"))
        assertEquals(ciphertext.size.toString(), reply.headers.getValue("content-length"))
        assertArrayEquals(ciphertext, reply.body)
    }

    @Test fun securityHeadersAreSentOnEveryReply() {
        val started = startOk()
        listOf("/s/${started.token.value}", "/nope").forEach {
            val headers = get(started.port, it).headers
            assertEquals(it, "no-store", headers["cache-control"])
            assertEquals(it, "nosniff", headers["x-content-type-options"])
            assertEquals(it, "no-referrer", headers["referrer-policy"])
            assertEquals(it, "close", headers["connection"])
        }
    }

    @Test fun mirrorRoutesAndWrongTokensAreGenericNotFoundWithoutCiphertext() {
        val started = startOk()
        listOf(
            "/m/${started.token.value}", "/m/${started.token.value}/state", "/m/${started.token.value}/events",
            "/s/${"0".repeat(32)}", "/s/${started.token.value}/state", "/s/${started.token.value}?x=1", "/",
        ).forEach {
            val reply = get(started.port, it)
            assertEquals(it, 404, reply.status)
            assertEquals(it, "Not found", String(reply.body))
        }
    }

    @Test fun sessionTokenExpiresAfterFiveMinutesNotThirtyMirrorMinutes() {
        val started = startOk()
        assertEquals(1_000L + SyncLimits.SESSION_TTL_MILLIS, started.token.expiresAtMillis)
        assertTrue(SyncLimits.SESSION_TTL_MILLIS < MirrorLimits.TOKEN_TTL_MILLIS)
        now = 1_000L + SyncLimits.SESSION_TTL_MILLIS - 1
        assertEquals(200, get(started.port, "/s/${started.token.value}").status)
        now = 1_000L + SyncLimits.SESSION_TTL_MILLIS
        assertEquals(404, get(started.port, "/s/${started.token.value}").status)
    }

    @Test fun retryUntilExpiryReturnsTheSameBytes() {
        val started = startOk()
        repeat(3) { assertArrayEquals(ciphertext, get(started.port, "/s/${started.token.value}").body) }
    }

    @Test fun onlyGetIsAcceptedAndOtherMethodsNeverReturnTheEnvelope() {
        val started = startOk()
        val reply = get(started.port, "/s/${started.token.value}", method = "POST")
        assertEquals(405, reply.status)
        assertFalse(reply.body.contentEquals(ciphertext))
    }

    @Test fun bindsExactlyTheGivenAddressAndStopClosesEverything() {
        val started = startOk()
        assertEquals(loopback, server.boundAddress)
        assertTrue(server.isRunning)
        server.stop()
        assertFalse(server.isRunning)
        assertTrue(runCatching { Socket(loopback, started.port).use { it.getInputStream().read() } }.let {
            it.isFailure || it.getOrNull() == -1
        })
    }

    @Test fun secondStartIsRejectedAndRestartIssuesAFreshToken() {
        val first = startOk()
        assertEquals(MirrorStartResult.Failed(MirrorFailure.ALREADY_RUNNING), server.startSync(loopback, ciphertext))
        server.stop()
        val second = startOk()
        assertTrue(first.token.value != second.token.value)
    }

    @Test fun envelopeAboveTheCapIsRefusedBeforeBinding() {
        val result = runCatching { server.startSync(loopback, ByteArray(SyncLimits.MAX_ENVELOPE_BYTES + 1)) }
        assertTrue(result.isFailure)
        assertFalse(server.isRunning)
    }

    @Test fun perAddressRateLimitStopsHammeringWithoutServingCiphertext() {
        val started = startOk()
        val statuses = (1..MirrorLimits.MAX_REQUESTS_PER_MINUTE + 3).map { get(started.port, "/s/${started.token.value}").status }
        assertEquals(MirrorLimits.MAX_REQUESTS_PER_MINUTE, statuses.count { it == 200 })
        assertEquals(3, statuses.count { it == 429 })
    }

    @Test fun tokenAndEnvelopeNeverAppearInTheErrorReply() {
        val started = startOk()
        val reply = get(started.port, "/s/${started.token.value}/x")
        val text = String(reply.body, Charsets.ISO_8859_1)
        assertFalse(text.contains(started.token.value))
    }
}
