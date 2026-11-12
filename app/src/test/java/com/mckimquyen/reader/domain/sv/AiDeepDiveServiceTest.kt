package com.mckimquyen.reader.domain.sv

import android.app.Application
import com.mckimquyen.reader.domain.ai.agents.DeepDivePolicy
import com.mckimquyen.reader.infrastructure.ai.AiRequestGateway
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AiDeepDiveServiceTest {

    private val gateway: AiRequestGateway = mockk()
    private val summaryService: GeminiSummaryService = mockk()
    private lateinit var service: AiDeepDiveService

    private val good = """{"context":["a","b","c"],"critical":[{"school":"s1","view":"v1"},{"school":"s2","view":"v2"}],"impact":[{"area":"x","shortTerm":"y","longTerm":"z"},{"area":"x2","shortTerm":"y2","longTerm":"z2"}]}"""

    @Before
    fun setUp() {
        every { summaryService.resolveApiKeys() } returns listOf("key-a")
        service = AiDeepDiveService(gateway, summaryService)
    }

    private fun run(title: String = "Rates rise", body: String = "<p>The bank lifted rates.</p>"): AiDeepDiveResult =
        runBlocking { service.generate("art-1", title, body, "en-US") }

    private fun promptOf(requestBody: String): String =
        JSONObject(requestBody).getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0).getString("text")

    @Test
    fun validAnswer_isReady() {
        coEvery { gateway.execute(any(), any(), any()) } returns good
        val r = run() as AiDeepDiveResult.Ready
        assertEquals("art-1", r.report.articleId)
        assertEquals(3, r.report.context.size)
    }

    @Test
    fun usesTheDeepDiveUseCaseAndTheResolvedKeys() {
        coEvery { gateway.execute(any(), any(), any()) } returns good
        run()
        coVerify(exactly = 1) { gateway.execute("deep_dive", any(), listOf("key-a")) }
    }

    @Test
    fun requestBodyHoldsOnlyTitleAndCleanedExcerpt() {
        val body = slot<String>()
        coEvery { gateway.execute(any(), capture(body), any()) } returns good
        run(title = "Rates rise", body = "<p>Central <b>bank</b> acts.</p>")

        val prompt = promptOf(body.captured)
        assertTrue(prompt.contains("Title: Rates rise"))
        assertTrue(prompt.contains("Excerpt: Central bank acts."))
        assertFalse(prompt.contains("<p>"))
        assertFalse(prompt.contains("art-1"))
    }

    @Test
    fun longBodyIsCutToTheExcerptLimitBeforeSending() {
        val body = slot<String>()
        coEvery { gateway.execute(any(), capture(body), any()) } returns good
        run(body = "z".repeat(DeepDivePolicy.MAX_EXCERPT_CHARS * 2))
        assertEquals(DeepDivePolicy.MAX_EXCERPT_CHARS, promptOf(body.captured).count { it == 'z' })
    }

    @Test
    fun nothingToAnalyze_neverCallsTheGateway() {
        val r = run(title = "  ", body = " <br/> ")
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.NOTHING_TO_ANALYZE), r)
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun noKey_neverCallsTheGateway() {
        every { summaryService.resolveApiKeys() } returns emptyList()
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.NO_KEY), run())
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun missingApiKeyException_mapsToNoKey() {
        coEvery { gateway.execute(any(), any(), any()) } throws GeminiSummaryService.SummaryException.MissingApiKey
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.NO_KEY), run())
    }

    @Test
    fun rateLimited_mapsToNetwork() {
        coEvery { gateway.execute(any(), any(), any()) } throws GeminiSummaryService.SummaryException.RateLimited
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.NETWORK), run())
    }

    @Test
    fun httpError_mapsToNetwork() {
        coEvery { gateway.execute(any(), any(), any()) } throws GeminiSummaryService.SummaryException.Http(500)
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.NETWORK), run())
    }

    @Test
    fun ioFailure_mapsToNetwork() {
        coEvery { gateway.execute(any(), any(), any()) } throws IOException("boom")
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.NETWORK), run())
    }

    @Test
    fun rejectedAnswer_mapsToRejected() {
        coEvery { gateway.execute(any(), any(), any()) } returns "not json at all"
        assertEquals(AiDeepDiveResult.Failed(AiDeepDiveFailure.REJECTED), run())
    }

    @Test
    fun cancellation_isRethrown_notSwallowed() {
        coEvery { gateway.execute(any(), any(), any()) } throws CancellationException("cancelled")
        var thrown: Throwable? = null
        try { run() } catch (e: CancellationException) { thrown = e }
        assertTrue(thrown is CancellationException)
    }
}
