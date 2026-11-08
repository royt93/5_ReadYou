package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.domain.model.podcast.PodcastScriptSource
import com.mckimquyen.reader.infrastructure.ai.AiRequestGateway
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PodcastStudioScriptServiceTest {

    private val gateway: AiRequestGateway = mockk()
    private val summaryService: GeminiSummaryService = mockk()
    private val commuteScriptService: CommuteScriptService = mockk()
    private lateinit var service: PodcastStudioScriptService

    private val goodJson = """[{"speaker":"ALEX","text":"Hi Sam"},{"speaker":"SAM","text":"Hi Alex"}]"""

    @Before fun setUp() {
        every { summaryService.resolveApiKeys() } returns listOf("key-a")
        every { commuteScriptService.parseDialoguesFromJson(any()) } answers {
            if (firstArg<String>() == goodJson) {
                listOf(
                    CommuteDialogue(CommuteSpeaker.ALEX, "Hi Sam"),
                    CommuteDialogue(CommuteSpeaker.SAM, "Hi Alex"),
                )
            } else {
                throw IllegalArgumentException("malformed")
            }
        }
        service = PodcastStudioScriptService(gateway, summaryService, commuteScriptService)
    }

    private fun article(
        title: String = "Title",
        short: String = "Short description",
        full: String? = "SECRET-FULL-CONTENT",
        link: String = "https://secret.example.com/path",
    ) = Article(
        id = "art_1", date = Date(0L), title = title, author = "SECRET-AUTHOR", rawDescription = "SECRET-RAW",
        shortDescription = short, fullContent = full, link = link, feedId = "SECRET-FEED-ID", accountId = 7,
    )

    @Test fun snippetIsCutToAtMost250Characters() {
        assertEquals(250, service.sanitizeSnippet("x".repeat(1000)).length)
        assertEquals("short", service.sanitizeSnippet("  short  "))
        assertEquals("", service.sanitizeSnippet("   "))
    }

    @Test fun promptNeverContainsAnythingExceptTitleAndShortPreview() {
        val prompt = service.buildPrompt("My Title", service.sanitizeSnippet("Preview text"), "en-US")
        assertTrue(prompt.contains("My Title"))
        assertTrue(prompt.contains("Preview text"))
        for (secret in listOf("SECRET", "example.com", "art_1", "accountId")) {
            assertFalse("prompt leaked $secret", prompt.contains(secret))
        }
    }

    @Test fun requestBodySentToGeminiOnlyCarriesTitleAndPreview() = runBlocking {
        val bodySlot = slot<String>()
        coEvery { gateway.execute(any(), capture(bodySlot), any()) } returns goodJson

        service.generate(article(short = "y".repeat(900)), useGemini = true, languageTag = "en-US")

        val body = bodySlot.captured
        for (secret in listOf("SECRET", "example.com", "art_1")) {
            assertFalse("request leaked $secret", body.contains(secret))
        }
        assertFalse("preview was not truncated", body.contains("y".repeat(251)))
        assertTrue(body.contains("y".repeat(250)))
    }

    @Test fun declinedConsentNeverCallsTheGateway() = runBlocking {
        val result = service.generate(article(), useGemini = false, languageTag = "en-US")

        assertEquals(PodcastScriptSource.HEURISTIC, result.source)
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test fun missingKeysNeverCallTheGatewayEvenWithConsent() = runBlocking {
        every { summaryService.resolveApiKeys() } returns emptyList()

        val result = service.generate(article(), useGemini = true, languageTag = "en-US")

        assertEquals(PodcastScriptSource.HEURISTIC, result.source)
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }

    @Test fun successfulGeminiReplyIsLabelledGeminiAndKeepsTheArticleId() = runBlocking {
        coEvery { gateway.execute("podcast_studio", any(), listOf("key-a")) } returns goodJson

        val result = service.generate(article(), useGemini = true, languageTag = "en-US")

        assertEquals(PodcastScriptSource.GEMINI, result.source)
        assertEquals(listOf("art_1"), result.episode.articleIds)
        assertEquals(listOf(CommuteSpeaker.ALEX, CommuteSpeaker.SAM), result.episode.dialogues.map { it.speaker })
    }

    @Test fun gatewayFailureFallsBackToHeuristicInsteadOfThrowing() = runBlocking {
        coEvery { gateway.execute(any(), any(), any()) } throws IOException("offline")

        val result = service.generate(article(), useGemini = true, languageTag = "en-US")

        assertEquals(PodcastScriptSource.HEURISTIC, result.source)
        assertTrue(result.episode.dialogues.isNotEmpty())
    }

    @Test fun malformedGeminiJsonFallsBackToHeuristic() = runBlocking {
        coEvery { gateway.execute(any(), any(), any()) } returns "this is not json"

        val result = service.generate(article(), useGemini = true, languageTag = "en-US")

        assertEquals(PodcastScriptSource.HEURISTIC, result.source)
    }

    @Test fun emptyDialogueFromGeminiFallsBackToHeuristic() = runBlocking {
        every { commuteScriptService.parseDialoguesFromJson(any()) } returns emptyList()
        coEvery { gateway.execute(any(), any(), any()) } returns "[]"

        val result = service.generate(article(), useGemini = true, languageTag = "en-US")

        assertEquals(PodcastScriptSource.HEURISTIC, result.source)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNeverSwallowedAsAFallback() {
        coEvery { gateway.execute(any(), any(), any()) } throws CancellationException("cancelled")
        runBlocking { service.generate(article(), useGemini = true, languageTag = "en-US") }
    }

    @Test fun heuristicScriptTalksAboutThatArticleAndUsesBothHosts() = runBlocking {
        val result = service.generate(article(title = "Mars Landing", short = "A probe touched down."), false, "en-US")

        val text = result.episode.dialogues.joinToString(" ") { it.text }
        assertTrue(text.contains("Mars Landing"))
        assertTrue(text.contains("A probe touched down."))
        assertEquals(setOf(CommuteSpeaker.ALEX, CommuteSpeaker.SAM), result.episode.dialogues.map { it.speaker }.toSet())
        assertFalse("must not reuse the morning bulletin greeting", text.contains("CommuteCast"))
    }

    @Test fun heuristicScriptIsLocalizedForVietnamese() = runBlocking {
        val result = service.generate(article(title = "Hạ cánh sao Hỏa", short = "Tàu thăm dò đã hạ cánh."), false, "vi-VN")

        val text = result.episode.dialogues.joinToString(" ") { it.text }
        assertTrue(text.contains("Hạ cánh sao Hỏa"))
        assertFalse("English template leaked into Vietnamese", text.contains("Welcome"))
    }

    @Test fun emptyArticleStillProducesAPlayableEpisodeWithoutContactingGemini() = runBlocking {
        val result = service.generate(article(title = "", short = ""), useGemini = true, languageTag = "en-US")

        assertEquals(PodcastScriptSource.HEURISTIC, result.source)
        assertTrue(result.episode.dialogues.size >= 2)
        coVerify(exactly = 0) { gateway.execute(any(), any(), any()) }
    }
}
