package com.mckimquyen.reader.infrastructure.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService.SummaryException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class AiRequestGatewayIntegrationTest {

    private lateinit var gateway: AiRequestGateway
    private lateinit var mockClient: OkHttpClient
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Before
    fun setUp() {
        mockClient = OkHttpClient.Builder().build()
    }

    private fun createGatewayWithMock(
        responseCode: Int = 200,
        responseBodyText: String = "Test content",
        delayMs: Long = 0,
        networkError: Boolean = false,
        onCancel: () -> Unit = {},
    ): Pair<AiRequestGateway, AtomicInteger> {
        val callCount = AtomicInteger(0)
        val interceptorClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                callCount.incrementAndGet()
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs)
                    } catch (e: InterruptedException) {
                        onCancel()
                        throw e
                    }
                }
                if (networkError) {
                    throw java.io.IOException("Simulated network failure on Android runtime")
                }
                val mockJson = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              { "text": "$responseBodyText" }
                            ]
                          }
                        }
                      ]
                    }
                """.trimIndent()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message(if (responseCode == 200) "OK" else "Error")
                    .body(mockJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val gw = AiRequestGateway(
            aiHttpClient = interceptorClient,
            externalScope = scope,
            ioDispatcher = Dispatchers.IO,
            forTesting = true,
        )
        return Pair(gw, callCount)
    }

    @Test
    fun execute_missingApiKey_throwsMissingApiKeyOnAndroidDevice() = runBlocking {
        val (gw, _) = createGatewayWithMock()
        try {
            gw.execute("testUseCase", "{}", emptyList())
            fail("Expected MissingApiKey exception")
        } catch (e: SummaryException.MissingApiKey) {
            assertNotNull(e)
        }
    }

    @Test
    fun execute_singleFlight_deduplicatesConcurrentCallsOnAndroidRuntime() = runBlocking {
        val (gw, callCount) = createGatewayWithMock(
            responseCode = 200,
            responseBodyText = "Deduplicated integration result",
            delayMs = 100,
        )

        val job1 = async {
            gw.execute("highlights", "{\"id\": 100}", listOf("api-key-1"))
        }
        val job2 = async {
            gw.execute("highlights", "{\"id\": 100}", listOf("api-key-1"))
        }

        val result1 = job1.await()
        val result2 = job2.await()

        assertEquals("Deduplicated integration result", result1)
        assertEquals("Deduplicated integration result", result2)
        assertEquals("Single-flight must execute exactly 1 HTTP call on Android runtime", 1, callCount.get())
    }

    @Test
    fun execute_cancellation_preservesRemainingWaitersOnAndroidRuntime() = runBlocking {
        val (gw, _) = createGatewayWithMock(
            responseCode = 200,
            responseBodyText = "Surviving waiter result",
            delayMs = 120,
        )

        val job1 = async {
            gw.execute("mindmap", "{\"query\": \"topic\"}", listOf("key1"))
        }
        val job2 = async {
            gw.execute("mindmap", "{\"query\": \"topic\"}", listOf("key1"))
        }

        delay(30)
        job1.cancel()

        try {
            job1.await()
        } catch (e: CancellationException) {
            // Expected
        }

        val result2 = job2.await()
        assertEquals("Surviving waiter result", result2)
    }
}
