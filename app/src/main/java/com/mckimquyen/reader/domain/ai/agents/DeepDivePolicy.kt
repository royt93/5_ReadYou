package com.mckimquyen.reader.domain.ai.agents

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class DeepDiveRejection {
    NOT_JSON, MISSING_FIELD, WRONG_CONTEXT_COUNT, WRONG_PERSPECTIVE_COUNT, WRONG_IMPACT_COUNT, EMPTY_TEXT, TEXT_TOO_LONG,
}

sealed interface DeepDiveParse {
    data class Valid(val report: DeepDiveReport) : DeepDiveParse
    data class Rejected(val reason: DeepDiveRejection) : DeepDiveParse
}

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
    private val FENCE = Regex("^```[a-zA-Z]*\\s*|\\s*```$")

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

    /** Structure only: nothing here can tell whether the content is true. */
    fun parseAndValidate(raw: String, articleId: String): DeepDiveParse {
        fun reject(r: DeepDiveRejection): DeepDiveParse = DeepDiveParse.Rejected(r)
        val obj = try {
            JSONObject(raw.trim().replace(FENCE, "").trim())
        } catch (_: JSONException) {
            return reject(DeepDiveRejection.NOT_JSON)
        }
        val contextArr = obj.optJSONArray("context")
        val criticalArr = obj.optJSONArray("critical")
        val impactArr = obj.optJSONArray("impact")
        if (contextArr == null || criticalArr == null || impactArr == null) return reject(DeepDiveRejection.MISSING_FIELD)
        if (contextArr.length() !in CONTEXT_MIN..CONTEXT_MAX) return reject(DeepDiveRejection.WRONG_CONTEXT_COUNT)
        if (criticalArr.length() !in PERSPECTIVE_MIN..PERSPECTIVE_MAX) return reject(DeepDiveRejection.WRONG_PERSPECTIVE_COUNT)
        if (impactArr.length() !in IMPACT_MIN..IMPACT_MAX) return reject(DeepDiveRejection.WRONG_IMPACT_COUNT)

        var tooLong = false
        // Returns null for a missing, non-string or blank value; flags [tooLong] when the cleaned text exceeds [max].
        fun text(value: Any?, max: Int): String? {
            val s = (value as? String)?.let(::tidy).orEmpty()
            if (s.isEmpty()) return null
            if (s.length > max) tooLong = true
            return s
        }

        val context = (0 until contextArr.length()).map { text(contextArr.opt(it), MAX_POINT_CHARS) }
        val perspectives = (0 until criticalArr.length()).map {
            val o = criticalArr.optJSONObject(it)
            val school = text(o?.opt("school"), MAX_SCHOOL_CHARS)
            val view = text(o?.opt("view"), MAX_VIEW_CHARS)
            if (school == null || view == null) null else DeepDivePerspective(school, view)
        }
        val impacts = (0 until impactArr.length()).map {
            val o = impactArr.optJSONObject(it)
            val area = text(o?.opt("area"), MAX_AREA_CHARS)
            val shortText = text(o?.opt("shortTerm"), MAX_IMPACT_CHARS)
            val longText = text(o?.opt("longTerm"), MAX_IMPACT_CHARS)
            if (area == null || shortText == null || longText == null) null else DeepDiveImpact(area, shortText, longText)
        }
        if (context.any { it == null } || perspectives.any { it == null } || impacts.any { it == null }) {
            return reject(DeepDiveRejection.EMPTY_TEXT)
        }
        if (tooLong) return reject(DeepDiveRejection.TEXT_TOO_LONG)
        return DeepDiveParse.Valid(
            DeepDiveReport(articleId, context.filterNotNull(), perspectives.filterNotNull(), impacts.filterNotNull())
        )
    }

    private fun tidy(value: String): String = value.replace(WHITESPACE, " ").trim()
}
