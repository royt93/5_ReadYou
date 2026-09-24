package com.mckimquyen.reader.infrastructure.ai

import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService.SummaryException
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After

class AiRequestGatewayTest {

    private lateinit var mockOkHttpClient: OkHttpClient
    private lateinit var gateway: AiRequestGateway
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0

        mockOkHttpClient = mockk(relaxed = true)
        gateway = AiRequestGateway(
            aiHttpClient = mockOkHttpClient,
            externalScope = scope,
            ioDispatcher = Dispatchers.IO,
            forTesting = true,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    private fun createMockCall(
        responseCode: Int = 200,
        responseJson: String = "",
        networkError: Boolean = false,
        onCancel: () -> Unit = {},
        delayMs: Long = 0,
    ): Call {
        val call = mockk<Call>(relaxed = true)
        every { call.cancel() } answers { onCancel() }

        every { call.enqueue(any()) } answers {
            val callback = it.invocation.args[0] as Callback
            Thread {
                if (delayMs > 0) Thread.sleep(delayMs)
                if (networkError) {
                    callback.onFailure(call, IOException("Simulated network failure"))
                } else {
                    val response = Response.Builder()
                        .request(Request.Builder().url("https://generativelanguage.googleapis.com").build())
                        .protocol(Protocol.HTTP_1_1)
                        .code(responseCode)
                        .message(if (responseCode == 200) "OK" else "Error")
                        .body(responseJson.toResponseBody("application/json".toMediaType()))
                        .build()
                    callback.onResponse(call, response)
                }
            }.start()
        }
        return call
    }

    private fun wrapGeminiResponse(text: String): String {
        val root = JsonObject().apply {
            val parts = JsonArray().apply {
                add(JsonObject().apply { addProperty("text", text) })
            }
            val content = JsonObject().apply {
                add("parts", parts)
            }
            val candidate = JsonObject().apply {
                add("content", content)
            }
            val candidates = JsonArray().apply {
                add(candidate)
            }
            add("candidates", candidates)
        }
        return root.toString()
    }

    @Test(expected = SummaryException.MissingApiKey::class)
    fun execute_emptyKeys_throwsMissingApiKey(): Unit = runBlocking {
        gateway.execute(
            useCase = "highlights",
            requestBody = "{}",
            keys = emptyList(),
        )
    }

    @Test
    fun execute_singleKeySuccess_returnsParsedText() = runBlocking {
        val expected = "Android 17 delivers powerful on-device intelligence."
        val mockCall = createMockCall(
            responseCode = 200,
            responseJson = wrapGeminiResponse(expected),
        )
        every { mockOkHttpClient.newCall(any()) } returns mockCall

        val result = gateway.execute(
            useCase = "highlights",
            requestBody = "{\"test\": 1}",
            keys = listOf("key_1"),
        )

        assertEquals(expected, result)
    }

    @Test
    fun execute_firstKeyRateLimited_failoversToSecondKey() = runBlocking {
        val expected = "Success from backup key"
        val call1 = createMockCall(responseCode = 429, responseJson = "Quota exceeded")
        val call2 = createMockCall(responseCode = 200, responseJson = wrapGeminiResponse(expected))

        every { mockOkHttpClient.newCall(any()) } returns call1 andThen call2

        val result = gateway.execute(
            useCase = "mindmap",
            requestBody = "{\"test\": 2}",
            keys = listOf("key_bad", "key_good"),
        )

        assertEquals(expected, result)
    }

    @Test
    fun execute_firstKeyInvalid_failoversToSecondKey() = runBlocking {
        val expected = "Success after invalid key"
        val call1 = createMockCall(responseCode = 403, responseJson = "API key not valid")
        val call2 = createMockCall(responseCode = 200, responseJson = wrapGeminiResponse(expected))

        every { mockOkHttpClient.newCall(any()) } returns call1 andThen call2

        val result = gateway.execute(
            useCase = "deepread",
            requestBody = "{\"test\": 3}",
            keys = listOf("key_invalid", "key_valid"),
        )

        assertEquals(expected, result)
    }

    @Test
    fun execute_allKeysFail_throwsLastException() = runBlocking {
        val call1 = createMockCall(responseCode = 429, responseJson = "Rate limited")
        val call2 = createMockCall(responseCode = 403, responseJson = "Invalid key")

        every { mockOkHttpClient.newCall(any()) } returns call1 andThen call2

        try {
            gateway.execute(
                useCase = "highlights",
                requestBody = "{\"test\": 4}",
                keys = listOf("key_1", "key_2"),
            )
            fail("Expected exception not thrown")
        } catch (e: SummaryException) {
            assertTrue(e is SummaryException.InvalidApiKey)
        }
    }

    @Test
    fun execute_networkError_throwsNetworkException() = runBlocking {
        val call = createMockCall(networkError = true)
        every { mockOkHttpClient.newCall(any()) } returns call

        try {
            gateway.execute(
                useCase = "highlights",
                requestBody = "{\"test\": 5}",
                keys = listOf("key_1"),
            )
            fail("Expected Network exception")
        } catch (e: SummaryException) {
            assertTrue(e is SummaryException.Network)
        }
    }

    @Test
    fun execute_singleFlight_duplicateConcurrentRequests_shareSingleNetworkCall() = runBlocking {
        val callCount = AtomicInteger(0)
        val expected = "Single flight answer"
        val mockCall = createMockCall(
            responseCode = 200,
            responseJson = wrapGeminiResponse(expected),
            delayMs = 80,
        )

        every { mockOkHttpClient.newCall(any()) } answers {
            callCount.incrementAndGet()
            mockCall
        }

        val deferred1 = async {
            gateway.execute("highlights", "{\"same\": \"body\"}", listOf("k1"))
        }
        val deferred2 = async {
            gateway.execute("highlights", "{\"same\": \"body\"}", listOf("k1"))
        }

        val res1 = deferred1.await()
        val res2 = deferred2.await()

        assertEquals(expected, res1)
        assertEquals(expected, res2)
        assertEquals(1, callCount.get())
    }

    @Test
    fun execute_cancellation_cancelsCallWhenLastWaiterLeaves() = runBlocking {
        val cancelled = AtomicBoolean(false)
        val mockCall = createMockCall(
            responseCode = 200,
            responseJson = wrapGeminiResponse("late"),
            delayMs = 250,
            onCancel = { cancelled.set(true) },
        )
        every { mockOkHttpClient.newCall(any()) } returns mockCall

        val job = launch {
            gateway.execute("highlights", "{\"cancel\": true}", listOf("k1"))
        }

        delay(30)
        job.cancel()

        delay(50)
        assertTrue("Expected OkHttp Call to be cancelled when sole waiter cancels", cancelled.get())
    }

    @Test
    fun execute_cancellation_firstWaiterLeaves_secondWaiterStillGetsResult() = runBlocking {
        val expected = "Resilient shared answer"
        val mockCall = createMockCall(
            responseCode = 200,
            responseJson = wrapGeminiResponse(expected),
            delayMs = 120,
        )
        every { mockOkHttpClient.newCall(any()) } returns mockCall

        val job1 = async {
            gateway.execute("highlights", "{\"shared\": 1}", listOf("k1"))
        }
        val job2 = async {
            gateway.execute("highlights", "{\"shared\": 1}", listOf("k1"))
        }

        delay(20)
        job1.cancel() // Caller 1 cancels

        try {
            job1.await()
        } catch (e: CancellationException) {
            // Expected for caller 1
        }

        val result2 = job2.await() // Caller 2 must still succeed
        assertEquals(expected, result2)
    }
}
