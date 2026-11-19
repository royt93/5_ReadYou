package com.mckimquyen.reader.infrastructure.social.nostr

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads one author's latest text notes from public Nostr relays. Read-only: it sends a single REQ and a CLOSE.
 * Nothing here logs: a relay sees the npub being read, so the app must not also write it down.
 */
@Singleton
class NostrRelayClient internal constructor(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val relays: List<String>,
    private val requireSecure: Boolean,
    private val timeoutMs: Long,
) {
    @Inject constructor(client: OkHttpClient, @IODispatcher ioDispatcher: CoroutineDispatcher) :
        this(client, ioDispatcher, RELAYS, requireSecure = true, timeoutMs = RELAY_TIMEOUT_MS)

    suspend fun fetch(authorHex: String): NostrFetchResult = withContext(ioDispatcher) {
        if (!AUTHOR_REGEX.matches(authorHex)) return@withContext NostrFetchResult.EMPTY
        var firstProfile: String? = null
        for (relay in relays) {
            if (requireSecure && !relay.startsWith(SECURE_SCHEME)) continue
            val result = readRelay(relay, authorHex)
            if (result.notes.isNotEmpty()) return@withContext result.copy(profileName = result.profileName ?: firstProfile)
            firstProfile = firstProfile ?: result.profileName
        }
        NostrFetchResult(emptyList(), firstProfile)
    }

    private suspend fun readRelay(url: String, authorHex: String): NostrFetchResult {
        val found = LinkedHashMap<String, NostrEvent>()
        val profiles = ArrayList<NostrEvent>()
        val finished = CompletableDeferred<Unit>()
        var frames = 0
        val request = runCatching { Request.Builder().url(url).build() }.getOrNull() ?: return NostrFetchResult.EMPTY
        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(
                        "[\"REQ\",\"$SUBSCRIPTION_ID\",{\"kinds\":[${NostrEvent.TEXT_NOTE_KIND}],\"authors\":[\"$authorHex\"],\"limit\":$MAX_EVENTS_READ},{\"kinds\":[${NostrEvent.PROFILE_KIND}],\"authors\":[\"$authorHex\"],\"limit\":$PROFILE_LIMIT}]",
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
                            else if (event != null && event.isValidProfile(authorHex)) profiles += event
                            if (frames >= MAX_EVENTS_READ) finished.complete(Unit)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    finished.complete(Unit)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    // The relay says it will send no more: do not wait for the timeout. (onClosed needs no handling: it only follows
                    // onClosing or our own close, by when the wait is already over.)
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
        return NostrFetchResult(
            notes = found.values.sortedByDescending { it.createdAt },
            profileName = profiles.sortedByDescending { it.createdAt }.firstNotNullOfOrNull { NostrProfileName.fromContent(it.content) },
        )
    }

    companion object {
        val RELAYS = listOf("wss://nos.lol", "wss://relay.primal.net")
        const val RELAY_TIMEOUT_MS = 8_000L
        const val MAX_MESSAGE_BYTES = 64 * 1024
        const val MAX_EVENTS_READ = 50
        const val MAX_NOTES_KEPT = 20
        private const val PROFILE_LIMIT = 1
        private const val SUBSCRIPTION_ID = "f2"
        private const val SECURE_SCHEME = "wss://"
        private const val NORMAL_CLOSE = 1000
        private val AUTHOR_REGEX = Regex("^[0-9a-f]{64}$")
    }
}
