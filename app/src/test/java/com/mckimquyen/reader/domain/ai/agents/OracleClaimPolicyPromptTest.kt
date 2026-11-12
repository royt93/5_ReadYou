package com.mckimquyen.reader.domain.ai.agents

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// org.json is a stub on the plain JVM, so this pure-logic test runs under Robolectric like the Deep Dive ones.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OracleClaimPolicyPromptTest {

    @Test
    fun limits_areHardCodedNumbers() {
        assertEquals("oracle_claim", OracleClaimPolicy.USE_CASE)
        assertEquals(500, OracleClaimPolicy.MAX_INPUT_CHARS)
        assertEquals(5, OracleClaimPolicy.MAX_ARTICLES_PER_RUN)
        assertEquals(2, OracleClaimPolicy.MAX_ATTEMPTS)
        assertEquals(160, OracleClaimPolicy.MAX_QUESTION_CHARS)
        assertEquals(240, OracleClaimPolicy.MAX_CRITERIA_CHARS)
        assertEquals(3, OracleClaimPolicy.MAX_HORIZON_YEARS)
        assertEquals(30, OracleClaimPolicy.RETENTION_DAYS)
        assertEquals(3, OracleClaimPolicy.RECENT_WINDOW_DAYS)
    }

    @Test
    fun sanitize_stripsHtmlAndCollapsesWhitespace() {
        val s = OracleClaimPolicy.sanitizeInput("  Rates <b>rise</b> ", "<p>The   bank\n will  act.</p>")
        assertEquals("Rates rise\nThe bank will act.", s)
    }

    @Test
    fun sanitize_dropsAnEmptyPartWithoutABlankLine() {
        assertEquals("Only title", OracleClaimPolicy.sanitizeInput("Only title", "  <br> "))
        assertEquals("Only description", OracleClaimPolicy.sanitizeInput(" ", "Only description"))
    }

    @Test
    fun sanitize_capsTheWholeInputAt500_keepingTheTitleFirst() {
        val s = OracleClaimPolicy.sanitizeInput("T".repeat(100), "d".repeat(900))
        assertEquals(500, s.length)
        assertTrue(s.startsWith("T".repeat(100) + "\n"))
    }

    @Test
    fun sanitize_aTitleLongerThanTheCapIsCutAt500() {
        assertEquals(500, OracleClaimPolicy.sanitizeInput("x".repeat(800), "ignored").length)
    }

    @Test
    fun sanitize_neverLeavesALoneHighSurrogateAtTheCut() {
        // 499 ASCII chars + one emoji (2 UTF-16 units): the cut at 500 would split the pair.
        val s = OracleClaimPolicy.sanitizeInput("a".repeat(499) + "😀", "")
        assertEquals(499, s.length)
        assertFalse(Character.isHighSurrogate(s.last()))
    }

    @Test
    fun hasContent_isFalseForBlank() {
        assertFalse(OracleClaimPolicy.hasContentToAnalyze(""))
        assertFalse(OracleClaimPolicy.hasContentToAnalyze("   "))
        assertTrue(OracleClaimPolicy.hasContentToAnalyze("x"))
    }

    @Test
    fun prompt_carriesOnlyTheInputAndTheLanguage() {
        val p = OracleClaimPolicy.buildPrompt("Rates rise\nThe bank will act.", "vi-VN")
        assertTrue(p.contains("Rates rise\nThe bank will act."))
        assertTrue(p.contains("vi-VN"))
        assertFalse(p.contains("http"))
    }

    @Test
    fun prompt_forbidsInventingAndAllowsNoClaim() {
        val p = OracleClaimPolicy.buildPrompt("x", "en")
        assertTrue(p.contains("hasClaim"))
        assertTrue(p.contains("Do not invent"))
        assertTrue(p.contains("no web access"))
        assertTrue(p.contains("yyyy-MM-dd"))
        assertTrue(p.contains("${OracleClaimPolicy.MAX_QUESTION_CHARS}"))
    }

    @Test
    fun requestBody_isJsonModeAndCarriesThePrompt() {
        val body = JSONObject(OracleClaimPolicy.buildRequestBody("PROMPT"))
        assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"))
        val text = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals("PROMPT", text)
    }
}
