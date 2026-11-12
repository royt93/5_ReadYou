package com.mckimquyen.reader.domain.ai.agents

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure rules for the background prediction finder (ORACLE-01): what may leave the device, how the request looks and which
 * answers are accepted. Privacy boundary: the only article data reachable from here is the title and a short
 * description, together at most [MAX_INPUT_CHARS]; the caller never hands over a link, a feed name or an account.
 */
object OracleClaimPolicy {
    const val USE_CASE = "oracle_claim"
    const val MAX_INPUT_CHARS = 500
    const val MAX_ARTICLES_PER_RUN = 5
    const val MAX_ATTEMPTS = 2
    const val MAX_QUESTION_CHARS = 160
    const val MAX_CRITERIA_CHARS = 240
    const val MAX_HORIZON_YEARS = 3
    const val RETENTION_DAYS = 30
    const val RECENT_WINDOW_DAYS = 3
    private const val TEMPERATURE = 0.2
    private const val JSON_MIME_TYPE = "application/json"
    private val HTML_TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    fun sanitizeInput(title: String, description: String): String {
        val parts = listOf(title, description)
            .map { it.replace(HTML_TAG, " ").replace(WHITESPACE, " ").trim() }
            .filter { it.isNotEmpty() }
        val joined = parts.joinToString("\n")
        if (joined.length <= MAX_INPUT_CHARS) return joined
        var end = MAX_INPUT_CHARS
        // Never cut between the two halves of a surrogate pair.
        if (Character.isHighSurrogate(joined[end - 1])) end--
        return joined.substring(0, end).trim()
    }

    fun hasContentToAnalyze(input: String): Boolean = input.isNotBlank()

    fun buildPrompt(input: String, languageTag: String): String = buildString {
        append("You find a verifiable prediction in ONE news item (title, then a short description).\n")
        append("Use ONLY the text below; no web access, no browsing, no sources.\n")
        append("Do not invent dates, figures, names or events that the text does not state.\n")
        append("Output STRICT JSON OBJECT ONLY, no markdown.\n")
        append("If the text states no future event that can later be checked as true or false, output {\"hasClaim\": false}.\n")
        append("Otherwise output {\"hasClaim\": true, \"question\": \"...\", \"expiresOn\": \"yyyy-MM-dd\", \"resolutionCriteria\": \"...\"} where:\n")
        append("\"question\" is a Yes/No question ending with a question mark, at most $MAX_QUESTION_CHARS characters;\n")
        append("\"expiresOn\" is the date the event is expected by, in the future, as yyyy-MM-dd;\n")
        append("\"resolutionCriteria\" says how anyone can later check the answer, at most $MAX_CRITERIA_CHARS characters.\n")
        append("Write the question and criteria in the language with tag $languageTag.\n\n")
        append("Text:\n")
        append(input)
    }

    fun buildRequestBody(prompt: String): String = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        put("generationConfig", JSONObject().put("temperature", TEMPERATURE).put("responseMimeType", JSON_MIME_TYPE))
    }.toString()
}
