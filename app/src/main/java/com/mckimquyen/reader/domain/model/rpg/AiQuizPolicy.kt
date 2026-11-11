package com.mckimquyen.reader.domain.model.rpg

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure rules for the AI quiz: what may leave the device, how the request looks, and (below) which answers are accepted.
 * Privacy boundary: the only article data reachable from here is the title and a preview of at most [MAX_PREVIEW_CHARS].
 */
object AiQuizPolicy {
    const val USE_CASE = "quiz_micro"
    const val MAX_PREVIEW_CHARS = 250
    const val OPTION_COUNT = 4
    private const val TEMPERATURE = 0.4
    private const val JSON_MIME_TYPE = "application/json"
    private val HTML_TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    fun sanitizePreview(raw: String): String =
        raw.replace(HTML_TAG, " ").replace(WHITESPACE, " ").trim().take(MAX_PREVIEW_CHARS).trim()

    fun hasContentToAsk(title: String, preview: String): Boolean = title.isNotBlank() || preview.isNotBlank()

    fun buildPrompt(title: String, preview: String, languageTag: String): String = buildString {
        append("You write ONE multiple-choice reading-comprehension question about ONE news article.\n")
        append("Use ONLY the title and preview below; do not invent details that they do not state.\n")
        append("Write exactly $OPTION_COUNT answer options: one correct, three plausible but clearly wrong.\n")
        append("Keep all options similar in length and style. Do not repeat the correct answer inside the question.\n")
        append("Output STRICT JSON OBJECT ONLY, no markdown: ")
        append("{\"question\": \"...\", \"options\": [\"...\", \"...\", \"...\", \"...\"], ")
        append("\"correctIndex\": 0, \"explanation\": \"one short sentence\"}\n")
        append("Target language tag: $languageTag.\n\n")
        append("Title: $title\n")
        append("Preview: $preview")
    }

    fun buildRequestBody(prompt: String): String = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        put("generationConfig", JSONObject().put("temperature", TEMPERATURE).put("responseMimeType", JSON_MIME_TYPE))
    }.toString()
}
