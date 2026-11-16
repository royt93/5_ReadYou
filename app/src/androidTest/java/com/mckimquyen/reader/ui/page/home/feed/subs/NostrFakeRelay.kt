package com.mckimquyen.reader.ui.page.home.feed.subs

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger

/** Plays the part of a Nostr relay on 127.0.0.1: answers one REQ with the note, the profile, then EOSE. Never touches the network. */
class NostrFakeRelay : AutoCloseable {
    private val server = MockWebServer().apply { start() }
    private val requests = AtomicInteger(0)
    @Volatile private var gate: CompletableDeferred<Unit>? = null
    val url: String = server.url("/").toString().replaceFirst("http://", "ws://")

    /** Holds the answer until [open] is completed, so a test can interrupt the Activity while the relay is still silent. */
    fun holdAnswerUntil(open: CompletableDeferred<Unit>) { gate = open }

    fun requestsReceived(): Int = requests.get()

    init {
        server.enqueue(socketResponse())
    }

    private fun socketResponse() = MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!text.startsWith("[\"REQ\"")) return
            requests.incrementAndGet()
            gate?.let { runBlocking { it.await() } }
            webSocket.send("[\"EVENT\",\"$SUBSCRIPTION_ID\",${NostrDeviceFixtures.NOTE_JSON}]")
            webSocket.send("[\"EVENT\",\"$SUBSCRIPTION_ID\",${NostrDeviceFixtures.PROFILE_JSON}]")
            webSocket.send("[\"EOSE\",\"$SUBSCRIPTION_ID\"]")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onOpen(webSocket: WebSocket, response: Response) = Unit
    })

    /** Opens the gate first: a test that failed while the relay was held would otherwise leave a blocked thread and make shutdown give up. */
    override fun close() {
        gate?.complete(Unit)
        server.shutdown()
    }

    private companion object {
        const val SUBSCRIPTION_ID = "f2"
    }
}
