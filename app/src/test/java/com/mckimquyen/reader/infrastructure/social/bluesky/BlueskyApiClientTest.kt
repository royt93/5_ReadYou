package com.mckimquyen.reader.infrastructure.social.bluesky

import android.app.Application
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.DID
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.HANDLE
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyTestPayloads.post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketPolicy
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BlueskyApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        http = OkHttpClient.Builder().followRedirects(true).callTimeout(15, TimeUnit.SECONDS).build()
    }

    @After fun tearDown() {
        http.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    private fun client(timeoutMs: Long = 800L, maxBody: Long = 64 * 1024L, base: String = server.url("/").toString().trimEnd('/')) =
        BlueskyApiClient(http, Dispatchers.IO, base, timeoutMs, maxBody)

    private val profile = """{"did":"$DID","handle":"$HANDLE","displayName":"Alice A","avatar":"https://cdn.bsky.app/img/avatar/a@jpeg"}"""
    private fun feed(vararg items: String) = """{"feed":[${items.joinToString(",")}]}"""
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun found(outcome: BlueskyOutcome): BlueskyFetchResult = (outcome as BlueskyOutcome.Found).result

    @Test fun twoCalls_profileThenFeed_andTheResultCarriesBoth() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(json(feed(post("3k1", "hi"))))
        val result = found(client().fetch(HANDLE))
        assertEquals(DID, result.did)
        assertEquals(HANDLE, result.handle)
        assertEquals("Alice A", result.displayName)
        assertEquals("https://cdn.bsky.app/img/avatar/a@jpeg", result.avatarUrl)
        assertEquals(1, result.items.length())
        // Compared decoded: the wire form is actor=did%3Aplc%3A..., which is what an HTTP client must send.
        val first = server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!
        val second = server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!
        assertEquals("/xrpc/app.bsky.actor.getProfile", first.encodedPath)
        assertEquals(HANDLE, first.queryParameter("actor"))
        assertEquals("/xrpc/app.bsky.feed.getAuthorFeed", second.encodedPath)
        assertEquals(DID, second.queryParameter("actor"))
        assertEquals("50", second.queryParameter("limit"))
        assertEquals("posts_no_replies", second.queryParameter("filter"))
    }

    @Test fun theFeedIsAskedByDid_notByTheTypedHandle() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(json(feed()))
        client().fetch("Alice.Bsky.Social")
        server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals(DID, server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("actor"))
    }

    @Test fun http400_isNotFound_andTheFeedIsNeverAsked() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"InvalidRequest","message":"Profile not found"}"""))
        assertEquals(BlueskyOutcome.NotFound, client().fetch("nobody.bsky.social"))
        assertEquals(1, server.requestCount)
    }

    @Test fun otherHttpErrors_areFailed_notNotFound() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
    }

    @Test fun http400WithAnotherMessage_isFailed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"InvalidRequest","message":"Invalid actor"}"""))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
    }

    @Test fun aRedirect_isFailed_andNeverFollowed() = runBlocking {
        val other = MockWebServer().apply { start() }
        try {
            other.enqueue(json(profile))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/").toString()))
            assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
            assertEquals(0, other.requestCount)
        } finally {
            other.shutdown()
        }
    }

    @Test fun brokenJson_isFailed() = runBlocking {
        server.enqueue(json("not json"))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
    }

    @Test fun aProfileWithoutADid_isFailed() = runBlocking {
        server.enqueue(json("""{"handle":"$HANDLE"}"""))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
    }

    @Test fun aFeedThatIsNotAnArray_isFailed() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(json("""{"feed":"nope"}"""))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
    }

    @Test fun aBodyOverTheLimit_isFailed_evenWhenTheLengthIsNotAnnounced() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setChunkedBody("{\"feed\":[" + "0,".repeat(40_000) + "0]}", 4096))
        assertEquals(BlueskyOutcome.Failed, client(maxBody = 8 * 1024L).fetch(HANDLE))
    }

    @Test fun aBodyJustUnderTheLimit_isAccepted() = runBlocking {
        server.enqueue(json(profile))
        val body = feed(post("3k1", "hi"))
        server.enqueue(json(body))
        assertTrue(client(maxBody = body.toByteArray().size.toLong()).fetch(HANDLE) is BlueskyOutcome.Found)
    }

    @Test fun aStalledServer_endsInBoundedTime_asFailed() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val t0 = System.nanoTime()
        assertEquals(BlueskyOutcome.Failed, client(timeoutMs = 500L).fetch(HANDLE))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0) < 4_000)
    }

    @Test fun aRefusedConnection_isFailed() = runBlocking {
        val port = server.port
        server.shutdown()
        assertEquals(BlueskyOutcome.Failed, client(base = "http://127.0.0.1:$port").fetch(HANDLE))
    }

    @Test fun anActorThatIsNotAHandleOrADid_isFailed_andNothingIsSent() = runBlocking {
        listOf("", "a b", "a/b", "a?x=1", "a&b", "ａ.bsky.social", "..", "x\u0000y", "a".repeat(300)).forEach {
            assertEquals(it, BlueskyOutcome.Failed, client().fetch(it))
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun aDidIsAccepted_asTheActor() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(json(feed()))
        assertTrue(client().fetch(DID) is BlueskyOutcome.Found)
        assertEquals(DID, server.takeRequest(1, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("actor"))
    }

    @Test fun cancellingTheCaller_stopsTheCall_andIsRethrown() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async { client(timeoutMs = 10_000L).fetch(HANDLE) }
        delay(200)
        job.cancel()
        var cancelled = false
        try { job.await() } catch (e: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertFalse(job.isActive)
    }

    @Test fun theUserAgentOrCookiesOfTheCaller_areNotAdded_andNoSecretIsSent() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(json(feed()))
        client().fetch(HANDLE)
        val headers = server.takeRequest(1, TimeUnit.SECONDS)!!.headers
        assertTrue(headers.names().none { it.equals("Authorization", true) || it.equals("Cookie", true) })
    }

    // The first bytes of these bodies are valid JSON, so a reader that stops at the limit and parses what it has would wrongly succeed.
    @Test fun aBodyOverTheLimit_isFailed_evenWhenItsFirstBytesAreValidJson() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setChunkedBody("{\"feed\":[]}" + " ".repeat(40_000), 4096))
        assertEquals(BlueskyOutcome.Failed, client(maxBody = 8 * 1024L).fetch(HANDLE))
    }

    @Test fun aBodyOverTheLimit_withAnAnnouncedLength_isFailed() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(json("{\"feed\":[]}" + " ".repeat(40_000)))
        assertEquals(BlueskyOutcome.Failed, client(maxBody = 8 * 1024L).fetch(HANDLE))
    }

    @Test fun cancellingTheCaller_cancelsTheHttpCallItself_notJustTheCoroutine() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val canceled = CountDownLatch(1)
        val watching = http.newBuilder().eventListener(object : EventListener() {
            override fun canceled(call: Call) = canceled.countDown()
        }).build()
        val job = async { BlueskyApiClient(watching, Dispatchers.IO, server.url("/").toString().trimEnd('/'), 10_000L, 64 * 1024L).fetch(HANDLE) }
        delay(200)
        job.cancel()
        assertTrue("the HTTP call must be cancelled, not left running", canceled.await(3, TimeUnit.SECONDS))
    }

    @Test fun aProfileWhoseDidIsNotADid_isFailed() = runBlocking {
        server.enqueue(json("""{"did":"alice.bsky.social","handle":"$HANDLE"}"""))
        server.enqueue(json(feed(post("3k1", "hi"))))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
    }

    // The DID of the profile goes into the second request, so a hostile profile must not be able to add a parameter or a path.
    @Test fun aProfileWhoseDidStartsRightButCarriesQueryCharacters_isFailed_andTheFeedIsNeverAsked() = runBlocking {
        listOf("did:plc:x&actor=evil", "did:plc:x/../y", "did:plc:x y", "did:plc:x#frag", "did:plc:" + "a".repeat(300)).forEach { did ->
            server.enqueue(json("""{"did":"$did","handle":"$HANDLE"}"""))
            assertEquals(did, BlueskyOutcome.Failed, client().fetch(HANDLE))
        }
        assertEquals("only the profile call may be made", 5, server.requestCount)
    }

    @Test fun theDisplayName_isCleaned_andANonTextOneIsIgnored() = runBlocking {
        server.enqueue(json("""{"did":"$DID","handle":"$HANDLE","displayName":"  Alice\n\u0007 A  "}"""))
        server.enqueue(json(feed()))
        assertEquals("Alice A", found(client().fetch(HANDLE)).displayName)
        server.enqueue(json("""{"did":"$DID","handle":"$HANDLE","displayName":123}"""))
        server.enqueue(json(feed()))
        assertNull(found(client().fetch(HANDLE)).displayName)
    }

    @Test fun theAvatar_isKeptOnlyWhenItIsPlainHttpsOnABlueskyHost() = runBlocking {
        listOf(
            "https://cdn.bsky.app/img/avatar/a@jpeg" to "https://cdn.bsky.app/img/avatar/a@jpeg",
            "http://cdn.bsky.app/img/avatar/a@jpeg" to null,
            "https://evil.example/a.jpg" to null,
        ).forEach { (avatar, expected) ->
            server.enqueue(json("""{"did":"$DID","handle":"$HANDLE","avatar":"$avatar"}"""))
            server.enqueue(json(feed()))
            assertEquals(avatar, expected, found(client().fetch(HANDLE)).avatarUrl)
        }
    }

    @Test fun aFeedThatSaysProfileNotFound_isFailed_becauseTheProfileJustExisted() = runBlocking {
        server.enqueue(json(profile))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"InvalidRequest","message":"Profile not found"}"""))
        assertEquals(BlueskyOutcome.Failed, client().fetch(HANDLE))
    }
}
