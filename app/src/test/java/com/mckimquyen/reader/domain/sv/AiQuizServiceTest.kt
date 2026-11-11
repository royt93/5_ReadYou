package com.mckimquyen.reader.domain.sv

import android.app.Application
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
class AiQuizServiceTest {

    private val gateway: AiRequestGateway = mockk()
    private val summaryService: GeminiSummaryService = mockk()
    private lateinit var service: AiQuizService

    private val good = """{"question":"Which body launched the rocket?","options":["The space agency","A shipping firm","A local council","A film studio"],"correctIndex":0,"explanation":"The preview names the agency."}"""

    @Before
    fun setUp() {
        every { summaryService.resolveApiKeys() } returns listOf("key-a")
        service = AiQuizService(gateway, summaryService)
    }

    private fun run(
        title: String = "Rocket launch",
        preview: String = "An agency launched a rocket today.",
    ): AiQuizResult = runBlocking { service.generate("art-1", title, preview, "General Knowledge", "en-US") }

    @Test
    fun aValidAnswerBecomesAReadyQuiz_withTheArticleIdAndCategory() {
        coEvery { gateway.execute(any(), any(), any()) } returns good

        val result = run() as AiQuizResult.Ready

        assertEquals("art-1", result.quiz.articleId)
        assertEquals("General Knowledge", result.quiz.category)
        assertEquals("The space agency", result.quiz.options[result.quiz.correctAnswerIndex])
    }

    @Test
    fun theRequestBodyHoldsOnlyTheTitleAndThePreview_nothingElseOfTheArticle() {
        val body = slot<String>()
        coEvery { gateway.execute(any(), capture(body), any()) } returns good

        run(title = "My Title", preview = "My preview")

        val text = JSONObject(body.captured).getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(text.contains("Title: My Title"))
        assertTrue(text.contains("Preview: My preview"))
        assertFalse("no link may be sent", body.captured.contains("http"))
        assertEquals(setOf("contents", "generationConfig"), JSONObject(body.captured).keys().asSequence().toSet())
    }

    @Test
    fun aPreviewLongerThan250CharactersIsCutBeforeItIsSent() {
        val body = slot<String>()
        coEvery { gateway.execute(any(), capture(body), any()) } returns good

        run(preview = "p".repeat(1_000))

        assertFalse(body.captured.contains("p".repeat(251)))
        assertTrue(body.captured.contains("p".repeat(250)))
    }

    @Test
    fun usesTheQuizUseCaseAndTheResolvedKeys() {
        coEvery { gateway.execute(any(), any(), any()) } returns good
        every { summaryService.resolveApiKeys() } returns listOf("own-key", "default-key")

        run()

        coVerify(exactly = 1) { gateway.execute("quiz_micro", any(), listOf("own-key", "default-key")) }
    }

    @Test
    fun blankTitleAndPreview_neverCallsTheGateway() {
        val result = run(title = "  ", preview = "")

        assertEquals(AiQuizResult.Failed(AiQuizFailure.NOTHING_TO_ASK), result)
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun noKeys_neverCallsTheGateway() {
        every { summaryService.resolveApiKeys() } returns emptyList()

        val result = run()

        assertEquals(AiQuizResult.Failed(AiQuizFailure.NO_KEY), result)
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test
    fun aNetworkOrKeyFailure_isAFailureNotACrash() {
        coEvery { gateway.execute(any(), any(), any()) } throws IOException("offline")

        assertEquals(AiQuizResult.Failed(AiQuizFailure.NETWORK), run())
    }

    @Test
    fun aMissingApiKeyException_isReportedAsNoKey() {
        coEvery { gateway.execute(any(), any(), any()) } throws GeminiSummaryService.SummaryException.MissingApiKey

        assertEquals(AiQuizResult.Failed(AiQuizFailure.NO_KEY), run())
    }

    @Test
    fun anAnswerThatFailsValidation_isRejected() {
        coEvery { gateway.execute(any(), any(), any()) } returns """{"question":"q","options":["a","b"],"correctIndex":0,"explanation":"x"}"""

        assertEquals(AiQuizResult.Failed(AiQuizFailure.REJECTED), run())
    }

    @Test
    fun cancellationIsNeverSwallowed() {
        coEvery { gateway.execute(any(), any(), any()) } throws CancellationException("cancelled")

        var thrown: Throwable? = null
        try {
            run()
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue("cancellation must propagate", thrown is CancellationException)
    }

    @Test
    fun noTitleOrPreviewTextIsEverAPartOfAnyFailureResult() {
        coEvery { gateway.execute(any(), any(), any()) } throws IOException("SECRET-TITLE failed")

        val result = run(title = "SECRET-TITLE")

        assertFalse(result.toString().contains("SECRET-TITLE"))
    }
}
