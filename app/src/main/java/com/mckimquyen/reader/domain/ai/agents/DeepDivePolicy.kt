package com.mckimquyen.reader.domain.ai.agents

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure rules for the AI Deep Dive: what may leave the device, how the request looks, and which answers are accepted.
 * Privacy boundary: the only article data reachable from here is the title and an excerpt of at most
 * [MAX_EXCERPT_CHARS]; the caller never hands over a link, a feed name or an account.
 */
object DeepDivePolicy {
    const val USE_CASE = "deep_dive"
    const val MAX_EXCERPT_CHARS = 6000
    const val CONTEXT_MIN = 3
    const val CONTEXT_MAX = 5
    const val PERSPECTIVE_MIN = 2
    const val PERSPECTIVE_MAX = 4
    const val IMPACT_MIN = 2
    const val IMPACT_MAX = 5
    const val MAX_POINT_CHARS = 220
    const val MAX_SCHOOL_CHARS = 40
    const val MAX_VIEW_CHARS = 240
    const val MAX_AREA_CHARS = 40
    const val MAX_IMPACT_CHARS = 160
    private const val TEMPERATURE = 0.4
    private const val JSON_MIME_TYPE = "application/json"
    private val HTML_TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    fun sanitizeExcerpt(raw: String): String {
        val cleaned = raw.replace(HTML_TAG, " ").replace(WHITESPACE, " ").trim()
        if (cleaned.length <= MAX_EXCERPT_CHARS) return cleaned
        var end = MAX_EXCERPT_CHARS
        // Never cut between the two halves of a surrogate pair.
        if (Character.isHighSurrogate(cleaned[end - 1])) end--
        return cleaned.substring(0, end).trim()
    }

    fun hasContentToAnalyze(title: String, excerpt: String): Boolean = title.isNotBlank() || excerpt.isNotBlank()

    fun buildPrompt(title: String, excerpt: String, languageTag: String): String = buildString {
        append("You are three analysts writing ONE one-page briefing about ONE news article.\n")
        append("Use ONLY the title and excerpt below plus general background knowledge; no web access, no browsing, no sources.\n")
        append("Do not invent figures, quotes, names or dates that the text does not state. If unsure, say so briefly.\n")
        append("Output STRICT JSON OBJECT ONLY, no markdown, with exactly these keys:\n")
        append("\"context\": array of $CONTEXT_MIN to $CONTEXT_MAX short strings (Context analyst: root causes and earlier developments),\n")
        append("\"critical\": array of $PERSPECTIVE_MIN to $PERSPECTIVE_MAX objects ")
        append("{\"school\": \"short label of the school of thought\", \"view\": \"its opposing or critical view\"} ")
        append("(Critical-thinking analyst),\n")
        append("\"impact\": array of $IMPACT_MIN to $IMPACT_MAX objects ")
        append("{\"area\": \"short area\", \"shortTerm\": \"effect within months\", \"longTerm\": \"effect over years\"} ")
        append("(Synthesis analyst: the impact matrix).\n")
        append("Keep every string concise. Target language tag: $languageTag.\n\n")
        append("Title: $title\n")
        append("Excerpt: $excerpt")
    }

    fun buildRequestBody(prompt: String): String = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        put("generationConfig", JSONObject().put("temperature", TEMPERATURE).put("responseMimeType", JSON_MIME_TYPE))
    }.toString()
}
