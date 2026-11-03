package com.mckimquyen.reader.infrastructure.mirror

import com.mckimquyen.reader.domain.p2psync.SyncSessionTransport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class LocalSyncTransportTest {
    private fun loopbackTransport() = SyncSessionTransport(2_000, 2_000) { it == "127.0.0.1" }

    @Test fun fetchesOpaqueBytesFromExactSessionPath() {
        val payload = ByteArray(64) { (it * 5 - 90).toByte() }
        ServerSocket(0).use { server ->
            server.soTimeout = 2_000
            val worker = thread {
                server.accept().use { socket ->
                    socket.soTimeout = 2_000
                    val request = socket.getInputStream().bufferedReader().readLine()
                    assertTrue(request.startsWith("GET /s/${"a".repeat(32)} HTTP/1.1"))
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(payload)
                    out.flush()
                }
            }
            val result = loopbackTransport().fetch("http://127.0.0.1:${server.localPort}/s/${"a".repeat(32)}")
            worker.join()
            assertArrayEquals(payload, result)
        }
    }

    @Test fun redirectsAreRejectedAndNeverFollowed() {
        ServerSocket(0).use { target ->
            target.soTimeout = 700
            var followed = false
            val targetWorker = thread {
                try {
                    target.accept().close()
                    followed = true
                } catch (_: java.net.SocketTimeoutException) {
                }
            }
            ServerSocket(0).use { server ->
                server.soTimeout = 2_000
                val worker = thread {
                    server.accept().use { socket ->
                        socket.soTimeout = 2_000
                        socket.getInputStream().bufferedReader().readLine()
                        val out = socket.getOutputStream()
                        out.write("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${target.localPort}/s/${"d".repeat(32)}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        out.flush()
                    }
                }
                assertTrue(runCatching {
                    loopbackTransport().fetch("http://127.0.0.1:${server.localPort}/s/${"b".repeat(32)}")
                }.isFailure)
                worker.join()
            }
            targetWorker.join()
            assertFalse("redirect target was contacted", followed)
        }
    }

    @Test fun responseLargerThanCapIsRejected() {
        ServerSocket(0).use { server ->
            server.soTimeout = 2_000
            val worker = thread {
                server.accept().use { socket ->
                    socket.soTimeout = 2_000
                    socket.getInputStream().bufferedReader().readLine()
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: ${8 * 1024 * 1024 + 1}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.flush()
                }
            }
            assertTrue(runCatching {
                loopbackTransport().fetch("http://127.0.0.1:${server.localPort}/s/${"c".repeat(32)}")
            }.isFailure)
            worker.join()
        }
    }
}
