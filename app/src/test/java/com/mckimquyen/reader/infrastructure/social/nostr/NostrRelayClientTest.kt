package com.mckimquyen.reader.infrastructure.social.nostr

import android.app.Application
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(DelicateCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NostrRelayClientTest {
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient
    private val received = CopyOnWriteArrayList<String>()
    private val closed = CountDownLatch(1)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        http = OkHttpClient()
    }

    @After fun tearDown() {
        http.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    private fun wsUrl() = server.url("/").toString()

    /** A relay that answers a REQ with [events] and then EOSE (unless [sendEose] is false). */
    private fun relay(vararg events: String, sendEose: Boolean = true): MockResponse =
        MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                received.add(text)
                if (text.startsWith("[\"REQ\"")) {
                    events.forEach { webSocket.send("[\"EVENT\",\"f2\",$it]") }
                    if (sendEose) webSocket.send("[\"EOSE\",\"f2\"]")
                }
                if (text.startsWith("[\"CLOSE\"")) closed.countDown()
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                closed.countDown()
                webSocket.close(code, reason)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = closed.countDown()
        })

    private fun client(vararg urls: String, secure: Boolean = false, timeoutMs: Long = 5_000L) =
        NostrRelayClient(http, Dispatchers.IO, urls.toList(), secure, timeoutMs)

    private fun ids(notes: List<NostrEvent>) = notes.map { it.id }

    @Test fun fetch_sendsOneReqForKind1AndTheAuthor_thenClosesTheSubscription() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PLAIN))
        val notes = client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(notes))
        assertTrue("socket closed", closed.await(3, TimeUnit.SECONDS))
        val req = received.first()
        assertEquals(
            "[\"REQ\",\"f2\",{\"kinds\":[1],\"authors\":[\"${NostrTestEvents.AUTHOR}\"],\"limit\":50}," +
                "{\"kinds\":[0],\"authors\":[\"${NostrTestEvents.AUTHOR}\"],\"limit\":1}]",
            req,
        )
        assertTrue("received=" + received.map { it.take(12) }, received.any { it == "[\"CLOSE\",\"f2\"]" })
    }

    @Test fun fetch_dropsNotesByOthersWrongKindsAndBadSignatures() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PLAIN, NostrTestEvents.FOREIGN, NostrTestEvents.KIND7))
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes))
    }

    @Test fun fetch_dropsANoteWhoseSignatureWasForged() = runBlocking {
        val forged = NostrTestEvents.PLAIN.replace("first note", "forged note")
        server.enqueue(relay(forged))
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes.isEmpty())
    }

    @Test fun fetch_ignoresEventsOfAnotherSubscription() = runBlocking {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    webSocket.send("[\"EVENT\",\"other\",${NostrTestEvents.PLAIN}]")
                    webSocket.send("[\"EOSE\",\"f2\"]")
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = webSocket.close(code, reason).let { }
            }),
        )
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes.isEmpty())
    }

    // FRONT-02y: fetch hands over EVERY valid event it read; the feed is cut to twenty later, after the replies are filtered out.
    @Test fun fetch_keepsEveryValidEventItRead_newestFirst() = runBlocking {
        server.enqueue(relay(*NostrTestEvents.MANY.toTypedArray()))
        val notes = client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        assertEquals(NostrTestEvents.MANY.size, notes.size)
        assertEquals(notes.sortedByDescending { it.createdAt }.map { it.id }, ids(notes))
        assertEquals(1_700_001_029L, notes.first().createdAt)
        assertEquals(1_700_001_000L, notes.last().createdAt)
    }

    /** Every valid note of the author that the fixtures hold: 8 single notes and the 30 of MANY (38, fewer than the 50 frames read). */
    private val everyValidNote: List<String> = listOf(
        NostrTestEvents.PLAIN, NostrTestEvents.RICH, NostrTestEvents.REPLY, NostrTestEvents.OLDER,
        NostrTestEvents.EMPTY, NostrTestEvents.FUTURE, NostrTestEvents.LONG, NostrTestEvents.QUOTES,
    ) + NostrTestEvents.MANY

    private fun junkFrames(count: Int): Array<String> = Array(count) { "{\"n\":$it}" }

    // The limit is on frames read (junk counts), not on notes kept: with exactly 50 frames every valid note is still read.
    @Test fun fetch_readsFiftyFramesAndKeepsEveryValidOne() = runBlocking {
        server.enqueue(relay(*junkFrames(NostrRelayClient.MAX_EVENTS_READ - everyValidNote.size), *everyValidNote.toTypedArray()))
        val notes = client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        assertEquals(everyValidNote.size, notes.size)
    }

    // One frame over the limit: the 51st frame (here the last valid note) is never read.
    @Test fun fetch_doesNotReadTheFiftyFirstFrame() = runBlocking {
        server.enqueue(relay(*junkFrames(NostrRelayClient.MAX_EVENTS_READ - everyValidNote.size + 1), *everyValidNote.toTypedArray()))
        val notes = client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        val lastSentId = requireNotNull(NostrEvent.parse(everyValidNote.last())).id
        assertEquals(everyValidNote.size - 1, notes.size)
        assertFalse("the last frame was the 51st and must not be read", ids(notes).contains(lastSentId))
    }

    @Test fun fetch_mergesTheSameNoteSentTwice() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PLAIN, NostrTestEvents.PLAIN))
        assertEquals(1, client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes.size)
    }

    // ---- failures and limits
    @Test fun fetch_triesTheSecondRelay_whenTheFirstRefusesTheUpgrade() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes))
    }

    @Test fun fetch_triesTheSecondRelay_whenTheFirstHasNoValidNotes() = runBlocking {
        server.enqueue(relay(NostrTestEvents.FOREIGN))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes))
    }

    // ---- FRONT-02z: a relay "had something" only when at least one of its notes can be SHOWN (not blank). Since FRONT-02ad a reply can be shown.

    // FRONT-02ad: a reply is shown like any note, so a relay that has only a reply has "something" and the second relay is not asked.
    @Test fun fetch_stopsAtTheFirstRelay_whenItHasOnlyAReply() = runBlocking {
        server.enqueue(relay(NostrTestEvents.REPLY))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        val notes = client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        assertEquals(1, notes.size)
        assertEquals("the second relay must not be asked", 1, server.requestCount)
    }

    @Test fun fetch_stopsAtTheFirstRelay_whenItHasAShowableNote() = runBlocking {
        server.enqueue(relay(NostrTestEvents.PLAIN))
        server.enqueue(relay(NostrTestEvents.OLDER))
        val notes = client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(notes))
        assertEquals("the second relay must not be asked", 1, server.requestCount)
    }

    @Test fun fetch_returnsEmpty_whenBothRelaysHaveOnlyABlankNote() = runBlocking {
        server.enqueue(relay(NostrTestEvents.EMPTY))
        server.enqueue(relay(NostrTestEvents.EMPTY))
        assertTrue(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes.isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test fun fetch_triesTheSecondRelay_whenTheFirstHasOnlyABlankNote() = runBlocking {
        server.enqueue(relay(NostrTestEvents.EMPTY))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes))
        assertEquals(2, server.requestCount)
    }

    @Test fun fetch_keepsTheFirstProfileName_whenTheNotesComeFromTheSecondRelay() = runBlocking {
        server.enqueue(relay(NostrTestEvents.EMPTY, NostrTestEvents.PROFILE))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        val result = client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR)
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(result.notes))
        assertEquals("Alice Liddell", result.profileName)
    }

    @Test fun fetch_returnsTheFirstProfileName_whenNeitherRelayHasAShowableNote() = runBlocking {
        server.enqueue(relay(NostrTestEvents.EMPTY, NostrTestEvents.PROFILE))
        server.enqueue(relay(NostrTestEvents.EMPTY))
        val result = client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR)
        assertTrue(result.notes.isEmpty())
        assertEquals("Alice Liddell", result.profileName)
    }

    // The relay that is used is used ALONE: what the first relay had (a blank note) does not travel along.
    @Test fun fetch_doesNotMergeTheTwoRelays() = runBlocking {
        server.enqueue(relay(NostrTestEvents.EMPTY))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes))
    }

    @Test fun fetch_returnsEmpty_whenBothRelaysFail() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes.isEmpty())
    }

    @Test fun fetch_timesOut_whenARelayNeverSendsEose_andThenTriesTheNextOne() = runBlocking {
        server.enqueue(relay(sendEose = false))
        server.enqueue(relay(NostrTestEvents.PLAIN))
        val started = System.currentTimeMillis()
        val notes = client(wsUrl(), wsUrl(), timeoutMs = 400L).fetch(NostrTestEvents.AUTHOR).notes
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(notes))
        assertTrue("took ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started < 4_000L)
    }

    // A VALID note padded with an unknown field past the limit: only the size check can reject it (it parses and verifies).
    @Test fun fetch_skipsAnOversizedFrame_evenWhenTheNoteInsideIsValid() = runBlocking {
        val padded = NostrTestEvents.PLAIN.dropLast(1) + ",\"pad\":\"" + "x".repeat(NostrRelayClient.MAX_MESSAGE_BYTES) + "\"}"
        server.enqueue(relay(padded, NostrTestEvents.OLDER))
        assertEquals(listOf("35194b7e72cd5edc18100d8782c35785b1b466ab8413953e789d2f95cc1eb4d4"), ids(client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes))
    }

    // Under the limit in characters but over it in UTF-8 bytes (each CJK character is 3 bytes).
    @Test fun fetch_measuresTheLimitInBytes_notInCharacters() = runBlocking {
        val chars = NostrRelayClient.MAX_MESSAGE_BYTES / 2
        val padded = NostrTestEvents.PLAIN.dropLast(1) + ",\"pad\":\"" + "\u4e2d".repeat(chars) + "\"}"
        server.enqueue(relay(padded, NostrTestEvents.OLDER))
        assertEquals(listOf("35194b7e72cd5edc18100d8782c35785b1b466ab8413953e789d2f95cc1eb4d4"), ids(client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes))
    }

    @Test fun fetch_countsAnOversizedFrameAmongTheFiftyItMayRead() = runBlocking {
        val padded = NostrTestEvents.PLAIN.dropLast(1) + ",\"pad\":\"" + "x".repeat(NostrRelayClient.MAX_MESSAGE_BYTES) + "\"}"
        server.enqueue(relay(*Array(NostrRelayClient.MAX_EVENTS_READ) { padded }, NostrTestEvents.OLDER))
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes.isEmpty())
    }

    // A relay that answers EOSE for ANOTHER subscription must not end ours: our own note arrives after it.
    @Test fun fetch_ignoresAnEoseOfAnotherSubscription() = runBlocking {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (!text.startsWith("[\"REQ\"")) return
                    webSocket.send("[\"EOSE\",\"other\"]")
                    webSocket.send("[\"EVENT\",\"f2\",${NostrTestEvents.PLAIN}]")
                    webSocket.send("[\"EOSE\",\"f2\"]")
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = webSocket.close(code, reason).let { }
            }),
        )
        assertEquals(listOf(NostrTestEvents.PLAIN_ID), ids(client(wsUrl()).fetch(NostrTestEvents.AUTHOR).notes))
    }

    @Test fun fetch_stopsReadingAfterFiftyFrames() = runBlocking {
        val junk = List(NostrRelayClient.MAX_EVENTS_READ) { "{\"n\":$it}" }
        server.enqueue(relay(*junk.toTypedArray(), NostrTestEvents.PLAIN))
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue("the 51st frame must not be read", client(wsUrl(), wsUrl()).fetch(NostrTestEvents.AUTHOR).notes.isEmpty())
    }

    @Test fun fetch_doesNotOpenAConnection_forAnInvalidAuthor() = runBlocking {
        for (bad in listOf("", "abc", NostrTestEvents.AUTHOR.uppercase(), NostrTestEvents.AUTHOR.dropLast(1), "\"],\"x\":[\"")) {
            assertTrue(bad, client(wsUrl()).fetch(bad).notes.isEmpty())
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun fetch_refusesARelayThatIsNotWss_whenSecurityIsRequired() = runBlocking {
        assertTrue(client(wsUrl(), secure = true).fetch(NostrTestEvents.AUTHOR).notes.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test fun production_relays_areTheTwoFixedSecureOnes() {
        assertEquals(listOf("wss://nos.lol", "wss://relay.primal.net"), NostrRelayClient.RELAYS)
        assertTrue(NostrRelayClient.RELAYS.all { it.startsWith("wss://") })
    }

    @Test fun fetch_closesTheSocketEvenWhenTheCallerIsCancelled() = runBlocking {
        server.enqueue(relay(sendEose = false))
        val job = GlobalScope.launch(Dispatchers.IO) { client(wsUrl(), timeoutMs = 30_000L).fetch(NostrTestEvents.AUTHOR) }
        delay(500L)
        job.cancel()
        assertTrue("socket closed after cancel", closed.await(3, TimeUnit.SECONDS))
    }

    @Test fun relayClient_neverLogsOrPrintsAnything() {
        val src = java.io.File("src/main/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrRelayClient.kt").readLines()
            .filterNot { it.trimStart().let { t -> t.startsWith("*") || t.startsWith("/*") || t.startsWith("//") || t.startsWith("import ") } }
            .joinToString("\n")
        listOf("Log.", "println", "printStackTrace").forEach { assertFalse(it, src.contains(it)) }
    }
}
