package com.mckimquyen.reader.infrastructure.social.nostr

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray

/**
 * Reads one author's latest text notes from public Nostr relays. Read-only: it sends a single REQ and a CLOSE.
 * Nothing here logs: a relay sees the npub being read, so the app must not also write it down.
 */
class NostrRelayClient internal constructor(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val relays: List<String>,
    private val requireSecure: Boolean,
    private val timeoutMs: Long,
) {
    constructor(client: OkHttpClient, ioDispatcher: CoroutineDispatcher) :
        this(client, ioDispatcher, RELAYS, requireSecure = true, timeoutMs = RELAY_TIMEOUT_MS)

    suspend fun fetch(authorHex: String): List<NostrEvent> = withContext(ioDispatcher) {
        if (!AUTHOR_REGEX.matches(authorHex)) return@withContext emptyList()
        for (relay in relays) {
            if (requireSecure && !relay.startsWith(SECURE_SCHEME)) continue
            val notes = readRelay(relay, authorHex)
            if (notes.isNotEmpty()) return@withContext notes
        }
        emptyList()
    }

    private suspend fun readRelay(url: String, authorHex: String): List<NostrEvent> {
        val found = LinkedHashMap<String, NostrEvent>()
        val finished = CompletableDeferred<Unit>()
        var frames = 0
        val request = runCatching { Request.Builder().url(url).build() }.getOrNull() ?: return emptyList()
        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(
                        "[\"REQ\",\"$SUBSCRIPTION_ID\",{\"kinds\":[${NostrEvent.TEXT_NOTE_KIND}],\"authors\":[\"$authorHex\"],\"limit\":$MAX_EVENTS_READ}]",
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (finished.isCompleted) return
                    if (text.length > MAX_MESSAGE_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) {
                        frames += 1
                        if (frames >= MAX_EVENTS_READ) finished.complete(Unit)
                        return
                    }
                    val message = runCatching { JSONArray(text) }.getOrNull() ?: return
                    when (message.optString(0)) {
                        "EOSE" -> if (message.optString(1) == SUBSCRIPTION_ID) finished.complete(Unit)
                        "EVENT" -> {
                            if (message.optString(1) != SUBSCRIPTION_ID) return
                            frames += 1
                            val raw = runCatching { message.getJSONObject(2).toString() }.getOrNull()
                            val event = raw?.let { NostrEvent.parse(it) }
                            if (event != null && event.isValid(authorHex)) found[event.id] = event
                            if (frames >= MAX_EVENTS_READ) finished.complete(Unit)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    finished.complete(Unit)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    finished.complete(Unit)
                }
            },
        )
        var answered = false
        try {
            withTimeout(timeoutMs) { finished.await() }
            answered = true
        } catch (_: TimeoutCancellationException) {
            // A relay that never answers is skipped; whatever it already sent is still checked below.
        } finally {
            if (answered) {
                // Close politely: cancel() right after send() would drop the queued CLOSE frame. OkHttp itself cancels
                // the socket if the relay does not finish the close handshake in time.
                socket.send("[\"CLOSE\",\"$SUBSCRIPTION_ID\"]")
                socket.close(NORMAL_CLOSE, null)
            } else {
                // Timeout or the caller was cancelled: nobody is listening any more, so drop the connection.
                socket.cancel()
            }
        }
        return found.values.sortedByDescending { it.createdAt }.take(MAX_NOTES_KEPT)
    }

    companion object {
        val RELAYS = listOf("wss://nos.lol", "wss://relay.primal.net")
        const val RELAY_TIMEOUT_MS = 8_000L
        const val MAX_MESSAGE_BYTES = 64 * 1024
        const val MAX_EVENTS_READ = 50
        const val MAX_NOTES_KEPT = 20
        private const val SUBSCRIPTION_ID = "f2"
        private const val SECURE_SCHEME = "wss://"
        private const val NORMAL_CLOSE = 1000
        private val AUTHOR_REGEX = Regex("^[0-9a-f]{64}$")
    }
}
