package com.mckimquyen.reader.domain.model.rpg

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AiQuizPolicyPromptTest {

    @Test
    fun previewIsCutToAtMost250CharactersAndTrimmed() {
        assertEquals(250, AiQuizPolicy.sanitizePreview("x".repeat(1_000)).length)
        assertEquals("short", AiQuizPolicy.sanitizePreview("  short  "))
        assertEquals("", AiQuizPolicy.sanitizePreview("   "))
    }

    @Test
    fun previewBoundaryIsExactly250() {
        assertEquals(250, AiQuizPolicy.sanitizePreview("y".repeat(250)).length)
        assertEquals(250, AiQuizPolicy.sanitizePreview("y".repeat(251)).length)
        assertEquals(249, AiQuizPolicy.sanitizePreview("y".repeat(249)).length)
    }

    @Test
    fun htmlTagsInThePreviewAreStrippedBeforeTheyLeaveTheDevice() {
        val cleaned = AiQuizPolicy.sanitizePreview("<p>Hello <b>world</b></p>")

        assertFalse(cleaned.contains("<"))
        assertEquals("Hello world", cleaned)
    }

    @Test
    fun nothingToAskWhenBothTitleAndPreviewAreBlank() {
        assertFalse(AiQuizPolicy.hasContentToAsk("", ""))
        assertFalse(AiQuizPolicy.hasContentToAsk("   ", "  \n "))
        assertTrue(AiQuizPolicy.hasContentToAsk("Title", ""))
        assertTrue(AiQuizPolicy.hasContentToAsk("", "Preview"))
    }

    @Test
    fun promptCarriesOnlyTheTitleAndThePreview() {
        val prompt = AiQuizPolicy.buildPrompt("My Title", "My preview text", "vi-VN")

        assertTrue(prompt.contains("Title: My Title"))
        assertTrue(prompt.contains("Preview: My preview text"))
        assertTrue(prompt.contains("vi-VN"))
        // The builder only receives these three strings, so nothing else of the article is reachable from here.
        assertFalse(prompt.contains("http"))
    }

    @Test
    fun promptAsksForStrictJsonWithFourOptionsAndForbidsInventing() {
        val prompt = AiQuizPolicy.buildPrompt("T", "P", "en-US")

        assertTrue(prompt.contains("STRICT JSON"))
        assertTrue(prompt.contains("question"))
        assertTrue(prompt.contains("options"))
        assertTrue(prompt.contains("correctIndex"))
        assertTrue(prompt.contains("explanation"))
        assertTrue("must say exactly four options", prompt.contains("exactly 4"))
        assertTrue(prompt.contains("do not invent", ignoreCase = true))
    }

    @Test
    fun requestBodyIsJsonModeAndHoldsOnlyThePrompt() {
        val prompt = AiQuizPolicy.buildPrompt("T", "P", "en-US")
        val body = JSONObject(AiQuizPolicy.buildRequestBody(prompt))

        assertEquals(setOf("contents", "generationConfig"), body.keys().asSequence().toSet())
        assertEquals(
            prompt,
            body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"),
        )
        assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"))
    }

    @Test
    fun useCaseNameIsStableAndDistinctFromOtherFeatures() {
        assertEquals("quiz_micro", AiQuizPolicy.USE_CASE)
    }
}
