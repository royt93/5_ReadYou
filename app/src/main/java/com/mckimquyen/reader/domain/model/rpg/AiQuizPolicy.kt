package com.mckimquyen.reader.domain.model.rpg

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Random

enum class AiQuizRejection {
    NOT_JSON, MISSING_FIELD, WRONG_OPTION_COUNT, EMPTY_OPTION, DUPLICATE_OPTIONS, BAD_CORRECT_INDEX,
    EMPTY_QUESTION, QUESTION_TOO_LONG, OPTION_TOO_LONG, OPTION_LENGTH_SKEW, ANSWER_LEAKED_IN_QUESTION, EMPTY_EXPLANATION,
}

sealed interface AiQuizParse {
    data class Valid(val quiz: QuizQuestion) : AiQuizParse
    data class Rejected(val reason: AiQuizRejection) : AiQuizParse
}

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
    const val MAX_QUESTION_CHARS = 200
    const val MAX_OPTION_CHARS = 120
    const val MAX_EXPLANATION_CHARS = 220
    const val MAX_LENGTH_RATIO = 2.5
    private const val MIN_LEAK_ANSWER_CHARS = 4
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val FENCE = Regex("^```[a-zA-Z]*\\s*|\\s*```$")

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

    fun parseAndValidate(raw: String, articleId: String, category: String): AiQuizParse {
        fun reject(r: AiQuizRejection): AiQuizParse = AiQuizParse.Rejected(r)
        val obj = try {
            JSONObject(raw.trim().replace(FENCE, "").trim())
        } catch (_: JSONException) {
            return reject(AiQuizRejection.NOT_JSON)
        }
        if (!obj.has("question") || !obj.has("options") || !obj.has("correctIndex") || !obj.has("explanation")) {
            return reject(AiQuizRejection.MISSING_FIELD)
        }
        val question = tidy(obj.optString("question", ""))
        val rawOptions = obj.optJSONArray("options") ?: return reject(AiQuizRejection.MISSING_FIELD)
        if (rawOptions.length() != OPTION_COUNT) return reject(AiQuizRejection.WRONG_OPTION_COUNT)
        val options = (0 until rawOptions.length()).map { tidy(rawOptions.optString(it, "")) }
        if (options.any { it.isEmpty() }) return reject(AiQuizRejection.EMPTY_OPTION)
        if (options.map(::fold).toSet().size != options.size) return reject(AiQuizRejection.DUPLICATE_OPTIONS)
        val correct = when (val v = obj.opt("correctIndex")) {
            is Int -> v
            is Long -> v.toInt()
            else -> null
        }
        if (correct == null || correct !in 0 until OPTION_COUNT) return reject(AiQuizRejection.BAD_CORRECT_INDEX)
        if (question.isEmpty()) return reject(AiQuizRejection.EMPTY_QUESTION)
        if (question.length > MAX_QUESTION_CHARS) return reject(AiQuizRejection.QUESTION_TOO_LONG)
        if (options.any { it.length > MAX_OPTION_CHARS }) return reject(AiQuizRejection.OPTION_TOO_LONG)
        val longest = options.maxOf { it.length }
        val shortest = options.minOf { it.length }
        if (longest.toDouble() / shortest > MAX_LENGTH_RATIO) return reject(AiQuizRejection.OPTION_LENGTH_SKEW)
        val answer = options[correct]
        if (answer.length >= MIN_LEAK_ANSWER_CHARS && fold(question).contains(fold(answer))) {
            return reject(AiQuizRejection.ANSWER_LEAKED_IN_QUESTION)
        }
        val explanation = tidy(obj.optString("explanation", ""))
        if (explanation.isEmpty()) return reject(AiQuizRejection.EMPTY_EXPLANATION)

        val shuffled = options.mapIndexed { i, text -> text to (i == correct) }.shuffled(Random(articleId.hashCode().toLong()))
        return AiQuizParse.Valid(
            QuizQuestion(
                articleId = articleId,
                question = question,
                options = shuffled.map { it.first },
                correctAnswerIndex = shuffled.indexOfFirst { it.second },
                explanation = explanation.take(MAX_EXPLANATION_CHARS),
                category = category,
            )
        )
    }

    private fun tidy(value: String): String = value.replace(WHITESPACE, " ").trim()

    private fun fold(value: String): String = value.lowercase().replace(NON_ALNUM, "")
}
