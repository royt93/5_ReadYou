package com.mckimquyen.reader.domain.ai.agents

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
class DeepDivePolicyPromptTest {

    @Test
    fun sanitize_stripsHtmlAndCollapsesWhitespace() {
        assertEquals("Hello big world", DeepDivePolicy.sanitizeExcerpt("<p>Hello   <b>big</b>\n\nworld</p>"))
    }

    @Test
    fun sanitize_capsAtTheExcerptLimit() {
        val out = DeepDivePolicy.sanitizeExcerpt("a".repeat(DeepDivePolicy.MAX_EXCERPT_CHARS + 500))
        assertEquals(DeepDivePolicy.MAX_EXCERPT_CHARS, out.length)
    }

    @Test
    fun sanitize_exactlyAtTheLimitIsKept() {
        val text = "b".repeat(DeepDivePolicy.MAX_EXCERPT_CHARS)
        assertEquals(text, DeepDivePolicy.sanitizeExcerpt(text))
    }

    @Test
    fun sanitize_neverLeavesALoneHighSurrogateAtTheCut() {
        val raw = "a".repeat(DeepDivePolicy.MAX_EXCERPT_CHARS - 1) + "😀" + "tail"
        val out = DeepDivePolicy.sanitizeExcerpt(raw)
        assertEquals(DeepDivePolicy.MAX_EXCERPT_CHARS - 1, out.length)
        assertFalse(Character.isHighSurrogate(out.last()))
    }

    @Test
    fun excerptLimitIsExactlySixThousand_theNumberTheConsentTextPromises() {
        assertEquals(6000, DeepDivePolicy.MAX_EXCERPT_CHARS)
        assertEquals(6000, DeepDivePolicy.sanitizeExcerpt("c".repeat(7000)).length)
    }

    @Test
    fun sanitize_blankStaysBlank() {
        assertEquals("", DeepDivePolicy.sanitizeExcerpt("  <br/>  \n "))
    }

    @Test
    fun hasContentToAnalyze_needsATitleOrAnExcerpt() {
        assertFalse(DeepDivePolicy.hasContentToAnalyze("  ", ""))
        assertTrue(DeepDivePolicy.hasContentToAnalyze("Title", ""))
        assertTrue(DeepDivePolicy.hasContentToAnalyze("", "Body"))
    }

    @Test
    fun prompt_carriesOnlyTitleAndExcerptAndTheLanguage() {
        val prompt = DeepDivePolicy.buildPrompt("Rates rise", "Central bank lifted rates.", "vi-VN")
        assertTrue(prompt.contains("Title: Rates rise"))
        assertTrue(prompt.contains("Excerpt: Central bank lifted rates."))
        assertTrue(prompt.contains("vi-VN"))
        assertFalse(prompt.contains("http"))
    }

    @Test
    fun prompt_forbidsInventingFactsAndWebLookups() {
        val prompt = DeepDivePolicy.buildPrompt("T", "E", "en-US")
        assertTrue(prompt.contains("Do not invent"))
        assertTrue(prompt.contains("no web"))
    }

    @Test
    fun prompt_describesTheThreeSectionsAndTheirLimits() {
        val prompt = DeepDivePolicy.buildPrompt("T", "E", "en-US")
        listOf("\"context\"", "\"critical\"", "\"impact\"", "\"school\"", "\"shortTerm\"", "\"longTerm\"")
            .forEach { assertTrue("missing $it", prompt.contains(it)) }
    }

    @Test
    fun requestBody_isJsonModeAndCarriesThePrompt() {
        val body = JSONObject(DeepDivePolicy.buildRequestBody("PROMPT"))
        assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"))
        val text = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals("PROMPT", text)
    }

    @Test
    fun useCaseNameIsStable() {
        assertEquals("deep_dive", DeepDivePolicy.USE_CASE)
    }
}
