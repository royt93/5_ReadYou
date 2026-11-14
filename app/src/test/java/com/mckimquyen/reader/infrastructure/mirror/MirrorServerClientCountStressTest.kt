package com.mckimquyen.reader.infrastructure.mirror

import com.mckimquyen.reader.domain.mirror.MirrorLimits
import com.mckimquyen.reader.domain.mirror.MirrorSnapshot
import com.mckimquyen.reader.domain.mirror.MirrorStartResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Opens and closes the maximum number of streams many times. The published client count must settle at the real number
 * of open streams every round. A fresh server (and a clock that only moves forward) is used for each batch so the rate
 * limiter and the 30-minute token never get in the way.
 */
class MirrorServerClientCountStressTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val pool = Executors.newFixedThreadPool(MirrorLimits.MAX_CLIENTS)

    private fun waitFor(timeoutSeconds: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(POLL_MILLIS)
    }

    @Test fun theClientCountSettlesAtTheRealNumberOfOpenStreams_everyRound() {
        try {
        repeat(BATCHES) { batch ->
            var now = START
            val server = MirrorServer(clock = { now })
            try {
                val started = server.start(loopback) as MirrorStartResult.Started
                repeat(ROUNDS_PER_BATCH) { round ->
                    now += MirrorLimits.RATE_WINDOW_MILLIS
                    val label = "batch $batch round $round"
                    // Opened from several threads at once: two workers then register their streams at the same moment.
                    val gate = CountDownLatch(1)
                    val pending = (1..MirrorLimits.MAX_CLIENTS).map {
                        pool.submit(Callable {
                            gate.await()
                            Socket(loopback, started.port).also { s ->
                                s.getOutputStream().write("GET /m/${started.token.value}/events HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                                s.soTimeout = READ_TIMEOUT_MILLIS
                                val status = s.getInputStream().bufferedReader().readLine()
                                check(status == "HTTP/1.1 200 OK") { "$label: a stream was refused: $status" }
                            }
                        })
                    }
                    gate.countDown()
                    val open = pending.map { it.get(WAIT_SECONDS * 2, TimeUnit.SECONDS) }
                    waitFor(WAIT_SECONDS) { server.clients.value == MirrorLimits.MAX_CLIENTS }
                    val seen = server.clients.value
                    open.forEach { it.close() }
                    assertEquals("$label: published count with all streams open", MirrorLimits.MAX_CLIENTS, seen)
                    waitFor(WAIT_SECONDS) {
                        server.publish(MirrorSnapshot("t", "s", 1L, listOf("p"), false, (batch * ROUNDS_PER_BATCH + round + 1).toLong()))
                        server.clients.value == 0
                    }
                    assertEquals("$label: published count with all streams closed", 0, server.clients.value)
                }
            } finally {
                server.stop()
            }
        }
        } finally {
            pool.shutdownNow()
        }
    }

    private companion object {
        const val BATCHES = 15
        const val ROUNDS_PER_BATCH = 20
        const val START = 1_000L
        const val POLL_MILLIS = 5L
        const val WAIT_SECONDS = 3L
        const val READ_TIMEOUT_MILLIS = 5_000
    }
}
